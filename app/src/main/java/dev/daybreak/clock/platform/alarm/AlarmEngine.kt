package dev.daybreak.clock.platform.alarm

import dev.daybreak.clock.ClockApplication
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.domain.TimeRules
import dev.daybreak.clock.domain.TimerRules
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class AlarmEngine(private val app: ClockApplication, private val store: BootStore) {
    private val mutex = Mutex()
    private val scheduler = AndroidAlarmScheduler(app)
    val sessions = store.sessions
    val timers = store.timers
    private fun boot() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun remaining(timer: TimerSession) = timer.remainingAt(boot(), SystemClock.elapsedRealtime(), System.currentTimeMillis())
    private suspend fun recordTimer(timer: TimerSession, session: RingingSession) {
        store.putTimerSession(timer, session)
        if (app.unlocked()) app.database.dao().saveRinging(session)
    }
    suspend fun startTimer(duration: Long, math: Boolean, vibrate: Boolean) = mutex.withLock {
        require(duration in 1000..TimerRules.MAX_DURATION) { "请设置 1 秒到 24 小时的时长" }
        require(store.allTimers().none { it.state in listOf("RUNNING", "PAUSED", "RINGING") }) { "请先结束当前计时器" }
        require(scheduler.allowed()) { "请先允许精确闹钟，再开始计时" }
        scheduleTimer(TimerSession(duration = duration, mathUnlockEnabled = math, vibrate = vibrate))
    }
    private suspend fun scheduleTimer(timer: TimerSession) {
        val now = System.currentTimeMillis()
        val running = timer.copy(state = "RUNNING", endAt = now + timer.remaining,
            endElapsed = SystemClock.elapsedRealtime() + timer.remaining, boot = boot())
        val time = Instant.ofEpochMilli(running.endAt).atZone(ZoneId.systemDefault())
        val question = MathChallenge.generate()
        val session = RingingSession(running.occurrenceId, "timer:${running.id}", 1, running.endAt,
            time.hour, time.minute, 0, "计时结束", null, "DEFAULT", "默认闹钟铃声", running.vibrate,
            running.mathUnlockEnabled, question.left, question.right, question.subtract, scheduleError = "正在排程")
        recordTimer(running, session)
        val error = scheduler.schedule(session)
        record(session.copy(scheduleError = error))
        if (error != null) throw IllegalStateException(error)
    }
    suspend fun pauseTimer(id: String) = mutex.withLock {
        val timer = store.timer(id) ?: return@withLock
        if (timer.state != "RUNNING") return@withLock
        val left = remaining(timer)
        require(left > 0) { "计时已结束，请进入响铃页面关闭" }
        val session = store.session(timer.occurrenceId) ?: return@withLock
        recordTimer(timer.copy(state = "PAUSED", remaining = left), session.copy(state = "CANCELLED"))
        scheduler.cancel(session)
    }
    suspend fun resumeTimer(id: String) = mutex.withLock {
        val timer = store.timer(id) ?: return@withLock
        if (timer.state != "PAUSED") return@withLock
        require(scheduler.allowed()) { "请先允许精确闹钟，再继续计时" }
        scheduleTimer(timer.copy(occurrenceId = UUID.randomUUID().toString()))
    }
    suspend fun cancelTimer(id: String) = mutex.withLock {
        val timer = store.timer(id) ?: return@withLock
        if (timer.state !in listOf("RUNNING", "PAUSED")) return@withLock
        require(timer.state != "RUNNING" || remaining(timer) > 0) { "计时已结束，请进入响铃页面关闭" }
        val session = store.session(timer.occurrenceId) ?: return@withLock
        recordTimer(timer.copy(state = "CANCELLED"), session.copy(state = "CANCELLED"))
        scheduler.cancel(session)
    }
    private suspend fun record(s: RingingSession) {
        store.putSession(s)
        if (app.unlocked()) app.database.dao().saveRinging(s)
    }
    suspend fun save(alarm: Alarm) = mutex.withLock {
        require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.days in 0..127)
        val dao = app.database.dao()
        val old = dao.alarm(alarm.id)
        val next = alarm.copy(revision = (old?.revision ?: 0L) + 1)
        // Fence pending callbacks before changing the credential-protected configuration.
        invalidate(alarm.id)
        store.removeAlarm(alarm.id)
        dao.saveAlarm(next)
        store.putAlarm(next)
        if (next.enabled) scheduleNext(next)
    }
    suspend fun delete(id: String) = mutex.withLock {
        invalidate(id)
        store.removeAlarm(id)
        app.database.dao().deleteAlarm(id)
    }
    private suspend fun invalidate(id: String) {
        store.allSessions().filter { it.alarmId == id && it.state == "PENDING" }.forEach {
            record(it.copy(state = "CANCELLED")); scheduler.cancel(it)
        }
    }
    private suspend fun scheduleNext(alarm: Alarm, now: Instant = Instant.now()) {
        if (!alarm.enabled) return
        val at = TimeRules.nextAlarm(alarm.hour, alarm.minute, alarm.days, now, ZoneId.systemDefault()).toEpochMilli()
        if (store.allSessions().any { it.alarmId == alarm.id && it.revision == alarm.revision && it.state == "PENDING" && it.scheduledAt == at }) return
        val question = MathChallenge.generate()
        val session = RingingSession(UUID.randomUUID().toString(), alarm.id, alarm.revision, at, alarm.hour,
            alarm.minute, alarm.days, alarm.label, alarm.ringtoneUri, alarm.ringtoneSource, alarm.ringtoneDisplayName,
            alarm.vibrate, alarm.mathUnlockEnabled, question.left, question.right, question.subtract,
            scheduleError = "正在排程")
        record(session)
        record(session.copy(scheduleError = scheduler.schedule(session)))
    }

    suspend fun fire(id: String): Boolean = mutex.withLock {
        val s = store.session(id) ?: return@withLock false
        if (s.state == "RINGING") return@withLock true
        if (s.state != "PENDING") return@withLock false
        if (s.isTimer) {
            val timer = store.timer(s.alarmId.removePrefix("timer:")) ?: return@withLock false
            if (timer.state != "RUNNING" || timer.occurrenceId != id) return@withLock false
            val left = remaining(timer)
            if (left > 0) {
                record(s.copy(scheduledAt = System.currentTimeMillis() + left,
                    scheduleError = scheduler.schedule(s, System.currentTimeMillis() + left)))
                return@withLock false
            }
            recordTimer(timer.copy(state = "RINGING", remaining = 0), s.copy(state = "RINGING", scheduleError = null))
            return@withLock true
        }
        if (s.scheduledAt > System.currentTimeMillis() + 2000L) return@withLock false
        val alarm = if (app.unlocked()) app.database.dao().alarm(s.alarmId) else store.alarm(s.alarmId)
        if (alarm == null || !alarm.enabled || alarm.revision != s.revision) {
            record(s.copy(state = "CANCELLED")); return@withLock false
        }
        record(s.copy(state = "RINGING", scheduleError = null))
        if (alarm.days == 0) {
            store.putAlarm(alarm.copy(enabled = false))
            if (app.unlocked()) app.database.dao().saveAlarm(alarm.copy(enabled = false))
        } else scheduleNext(alarm)
        true
    }
    suspend fun dismiss(id: String, answer: String?): Boolean = mutex.withLock {
        val s = store.session(id) ?: return@withLock false
        if (s.state != "RINGING") return@withLock false
        if (s.mathUnlockEnabled && !MathChallenge(s.left, s.right, s.subtract).accepts(answer.orEmpty())) return@withLock false
        val timer = if (s.isTimer) store.timer(s.alarmId.removePrefix("timer:")) else null
        if (timer != null) recordTimer(timer.copy(state = "ENDED"), s.copy(state = "ENDED"))
        else record(s.copy(state = "ENDED"))
        true
    }
    suspend fun markFallback(id: String) = mutex.withLock {
        store.session(id)?.let { record(it.copy(fallbackUsed = true)) }
    }

    suspend fun recover(recompute: Boolean = false) = mutex.withLock {
        if (app.unlocked()) {
            val dao = app.database.dao()
            // Merge journal outcomes first; no FK can cascade active occurrences away.
            store.allSessions().forEach { dao.saveRinging(it) }
            val alarms = dao.alarms().map { alarm ->
                val consumed = store.allSessions().any { it.alarmId == alarm.id && it.revision == alarm.revision &&
                    it.days == 0 && it.state in listOf("RINGING", "ENDED", "MISSED") }
                if (consumed && alarm.enabled) alarm.copy(enabled = false).also { dao.saveAlarm(it) } else alarm
            }
            store.replaceAlarms(alarms)
        }
        val now = System.currentTimeMillis()
        store.allSessions().filter { it.state == "PENDING" }.forEach { s ->
            if (s.isTimer) {
                val timer = store.timer(s.alarmId.removePrefix("timer:"))
                if (timer == null || timer.state != "RUNNING" || timer.occurrenceId != s.id) {
                    record(s.copy(state = "CANCELLED")); scheduler.cancel(s)
                } else {
                    val left = remaining(timer)
                    val at = now + left
                    // Re-anchor after a reboot; keep the monotonic deadline during clock/timezone edits.
                    store.putTimer(timer.copy(endAt = at, endElapsed = SystemClock.elapsedRealtime() + left, boot = boot()))
                    record(s.copy(scheduledAt = at, scheduleError = scheduler.schedule(s, maxOf(at, now + 1500))))
                }
                return@forEach
            }
            val alarm = store.alarm(s.alarmId)
            when {
                alarm == null || !alarm.enabled || alarm.revision != s.revision -> {
                    record(s.copy(state = "CANCELLED")); scheduler.cancel(s)
                }
                recompute && s.scheduledAt > now -> { record(s.copy(state = "CANCELLED")); scheduler.cancel(s) }
                s.scheduledAt < now - 600_000 -> {
                    record(s.copy(state = "MISSED"))
                    if (alarm.days == 0) {
                        val disabled = alarm.copy(enabled = false)
                        store.putAlarm(disabled)
                        if (app.unlocked()) app.database.dao().saveAlarm(disabled)
                    }
                }
                else -> record(s.copy(scheduleError = scheduler.schedule(s, maxOf(s.scheduledAt, now + 1500))))
            }
        }
        // Boot receivers only restore AlarmManager entries. The next real alarm starts playback.
        store.allSessions().filter { it.state == "RINGING" }.forEach { scheduler.schedule(it, now + 2000) }
        store.alarms().filter { it.enabled }.forEach { alarm ->
            if (store.allSessions().none { it.alarmId == alarm.id && it.revision == alarm.revision && it.state == "PENDING" }) scheduleNext(alarm)
        }
    }
}
