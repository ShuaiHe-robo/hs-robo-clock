package dev.daybreak.clock.platform.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import dev.daybreak.clock.ClockApplication
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.EmergencyWait
import dev.daybreak.clock.domain.TimeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class FocusEngine(private val app: ClockApplication) {
    private val mutex = Mutex()
    private val active = MutableStateFlow<List<FocusSession>>(emptyList())
    val sessions = active.asStateFlow()
    private val dao get() = app.database.dao()
    private fun boot() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)

    suspend fun refresh() = mutex.withLock { if (app.unlocked()) refreshInternal() }
    private suspend fun refreshInternal() {
        val now = System.currentTimeMillis()
        val rules = dao.focusRules()
        val currentBoot = boot()
        val extensionBoundaries = mutableListOf<Long>()
        app.database.withTransaction {
            dao.protectedFocusSessions().forEach { session ->
                if (now >= session.endAt) dao.saveFocusSession(session.copy(state = "EXPIRED"))
                else if (session.state == "RELEASE_PENDING" && (session.waitBoot != currentBoot || SystemClock.elapsedRealtime() < session.waitElapsed))
                    dao.saveFocusSession(session.copy(waitStartedAt = now, waitElapsed = SystemClock.elapsedRealtime(), waitBoot = currentBoot))
            }
            rules.filter { it.enabled }.forEach { rule ->
                val window = TimeRules.focusWindow(rule.startMinute, rule.endMinute, rule.days, Instant.ofEpochMilli(now), ZoneId.systemDefault())
                if (window != null) {
                    val id = "${rule.id}:${window.start.toEpochMilli()}"
                    val endAt = window.end.toEpochMilli()
                    // Include earlier extensions, even released ones, so refresh cannot
                    // resurrect protection from an expired parent window.
                    val latest = dao.focusWindowSessions(id).maxByOrNull { it.endAt }
                    if (latest == null) {
                        dao.saveFocusSession(FocusSession(id, rule.id, rule.label,
                            window.start.toEpochMilli(), endAt, rule.packages))
                    } else if (endAt > latest.endAt) {
                        // Keep the existing snapshot and its release intact until it ends.
                        // The additional interval is a new snapshot of the edited plan.
                        if (now >= latest.endAt) {
                            dao.saveFocusSession(FocusSession("$id:$endAt", rule.id, rule.label,
                                now, endAt, rule.packages))
                        } else extensionBoundaries += latest.endAt
                    }
                }
            }
        }
        active.value = dao.protectedFocusSessions().filter { now < it.endAt }
        val boundaries = rules.filter { it.enabled }.map {
            TimeRules.nextFocusBoundary(it.startMinute, it.endMinute, it.days, Instant.ofEpochMilli(now), ZoneId.systemDefault()).toEpochMilli()
        } + active.value.map { it.endAt } + extensionBoundaries
        scheduleBoundary(boundaries.minOrNull())
    }
    suspend fun save(rule: FocusRule) = mutex.withLock {
        require(rule.startMinute in 0..1439 && rule.endMinute in 0..1439 && rule.startMinute != rule.endMinute)
        require(rule.days in 1..127 && rule.targets().isNotEmpty())
        require(rule.targets().none { ProtectedApps(app).isProtected(it) })
        refreshInternal()
        dao.saveFocusRule(rule)
        refreshInternal()
    }
    suspend fun delete(id: String) = mutex.withLock {
        refreshInternal(); dao.deleteFocusRule(id); refreshInternal()
    }
    fun blockedPackages() = active.value.flatMap { it.targets() }.toSet()
    fun remaining(token: String): Long {
        val sessions = active.value.filter { it.releaseToken == token && it.state == "RELEASE_PENDING" }
        if (sessions.isEmpty()) return 0
        return sessions.maxOf { EmergencyWait.remaining(it.waitElapsed, it.waitBoot, SystemClock.elapsedRealtime(), boot()) }
    }
    suspend fun beginRelease(): String? = mutex.withLock {
        refreshInternal()
        val sessions = active.value
        if (sessions.isEmpty()) return@withLock null
        val existing = sessions.firstOrNull { it.state == "RELEASE_PENDING" }?.releaseToken
        if (existing != null) return@withLock existing
        val token = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        app.database.withTransaction {
            sessions.forEach { dao.saveFocusSession(it.copy(state = "RELEASE_PENDING", releaseToken = token,
                waitStartedAt = now, waitElapsed = elapsed, waitBoot = boot())) }
        }
        refreshInternal()
        token
    }
    suspend fun cancelRelease(token: String) = mutex.withLock {
        app.database.withTransaction {
            dao.releaseSessions(token).filter { it.state == "RELEASE_PENDING" }.forEach {
                dao.saveFocusSession(it.copy(state = "ACTIVE", releaseToken = null, waitElapsed = 0, waitStartedAt = 0, waitBoot = -1))
            }
        }
        refreshInternal()
    }
    suspend fun confirmRelease(token: String): Boolean = mutex.withLock {
        refreshInternal()
        val sessions = dao.releaseSessions(token).filter { it.state == "RELEASE_PENDING" && it.endAt > System.currentTimeMillis() }
        if (sessions.isEmpty() || sessions.any { EmergencyWait.remaining(it.waitElapsed, it.waitBoot, SystemClock.elapsedRealtime(), boot()) > 0 }) return@withLock false
        app.database.withTransaction {
            sessions.forEach { dao.saveFocusSession(it.copy(state = "EMERGENCY_RELEASED", releasedAt = System.currentTimeMillis())) }
        }
        refreshInternal()
        true
    }
    private var boundaryAt: Long? = null
    private fun scheduleBoundary(at: Long?) {
        if (boundaryAt == at) return
        val manager = app.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(app, 2002, Intent(app, FocusBoundaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        if (at != null) {
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (e: SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
        }
        // Cache only a successful schedule so a transient failure is retried.
        boundaryAt = at
    }
}
