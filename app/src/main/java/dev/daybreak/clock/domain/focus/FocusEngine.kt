package dev.daybreak.clock.domain.focus

import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.EmergencyWait
import dev.daybreak.clock.domain.TimeRules
import dev.daybreak.clock.domain.execution.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

/** Owns window snapshots and release semantics. Android only stores, wakes and presents. */
class FocusEngine(private val store: FocusExecutionStore, private val core: ExecutionCore,
    private val targets: FocusTargetPolicy) {
    private val active = MutableStateFlow<List<FocusSession>>(emptyList())
    val sessions = active.asStateFlow()
    private val mutableBoundary = MutableStateFlow(FocusBoundary())
    val boundary = mutableBoundary.asStateFlow()
    private data class Projection(val sessions: List<FocusSession>, val boundaryAt: Long?)

    private suspend fun FocusRecords.transition(session: FocusSession, next: FocusSession) {
        check(session.lifecycle().permits(next.lifecycle())) { "Invalid focus transition ${session.state} -> ${next.state}" }
        saveSession(next)
    }
    private suspend fun FocusRecords.reconcile(now: ExecutionTime): Projection {
        val instant = Instant.ofEpochMilli(now.wallMillis)
        val rules = rules()
        val extensions = mutableListOf<Long>()
        protectedSessions().forEach { session ->
            when {
                now.wallMillis >= session.endAt -> transition(session, session.copy(state = FocusState.EXPIRED.name))
                session.lifecycle() == FocusState.RELEASE_PENDING &&
                    (session.waitBoot != now.bootCount || now.elapsedMillis < session.waitElapsed) ->
                    transition(session, session.copy(waitStartedAt = now.wallMillis, waitElapsed = now.elapsedMillis, waitBoot = now.bootCount))
            }
        }
        rules.filter { it.enabled }.forEach { rule ->
            val window = TimeRules.focusWindow(rule.startMinute, rule.endMinute, rule.days, instant, now.zone) ?: return@forEach
            val id = "${rule.id}:${window.start.toEpochMilli()}"
            val end = window.end.toEpochMilli()
            // Include terminal extensions so an expired parent never resurrects a released interval.
            val latest = windowSessions(id).maxByOrNull { it.endAt }
            when {
                latest == null -> saveSession(FocusSession(id, rule.id, rule.label, window.start.toEpochMilli(), end, rule.packages))
                end > latest.endAt && now.wallMillis >= latest.endAt ->
                    saveSession(FocusSession("$id:$end", rule.id, rule.label, now.wallMillis, end, rule.packages))
                end > latest.endAt -> extensions += latest.endAt
            }
        }
        val sessions = protectedSessions().filter { now.wallMillis < it.endAt }
        val boundaries = rules.filter { it.enabled }.map {
            TimeRules.nextFocusBoundary(it.startMinute, it.endMinute, it.days, instant, now.zone).toEpochMilli()
        } + sessions.map { it.endAt } + extensions
        return Projection(sessions, boundaries.minOrNull())
    }
    private fun publish(projection: Projection, force: Boolean = false) {
        active.value = projection.sessions
        val at = projection.boundaryAt
        val result = if (at == null) { core.cancel(WakeKey.focus); ScheduleResult() }
            else core.schedule(WakeKey.focus, at, force)
        mutableBoundary.value = FocusBoundary(at, result.exact, result.error)
    }
    suspend fun refresh() = core.execute { now -> publish(store.transaction { reconcile(now) }) }
    suspend fun recover(reason: RecoveryReason) = core.execute { now ->
        // A matching local deadline does not prove the platform registration survived.
        publish(store.transaction { reconcile(now) }, force = true)
    }
    suspend fun boundaryDelivered() = core.execute { now ->
        core.delivered(WakeKey.focus)
        publish(store.transaction { reconcile(now) })
    }
    suspend fun save(rule: FocusRule) = core.execute { now ->
        require(rule.startMinute in 0..1439 && rule.endMinute in 0..1439 && rule.startMinute != rule.endMinute)
        require(rule.days in 1..127 && rule.targets().isNotEmpty())
        require(rule.targets().none { targets.isProtected(it) })
        val projection = store.transaction {
            reconcile(now) // Capture the current window before an edit can change the plan.
            saveRule(rule)
            reconcile(now)
        }
        publish(projection)
    }
    suspend fun delete(id: String) = core.execute { now ->
        publish(store.transaction { reconcile(now); deleteRule(id); reconcile(now) })
    }
    fun blockedPackages() = active.value.flatMap { it.targets() }.toSet()
    private fun remaining(token: String, now: ExecutionTime): Long = active.value
        .filter { it.releaseToken == token && it.lifecycle() == FocusState.RELEASE_PENDING }
        .maxOfOrNull { EmergencyWait.remaining(it.waitElapsed, it.waitBoot, now.elapsedMillis, now.bootCount) } ?: 0
    fun remaining(token: String) = remaining(token, core.now())
    fun presentation(packageName: String?): FocusPresentation? {
        if (packageName == null || targets.isProtected(packageName)) return null
        val now = core.now()
        val relevant = active.value.filter { packageName in it.targets() && it.endAt > now.wallMillis }
        val end = relevant.maxOfOrNull { it.endAt } ?: return null
        val token = active.value.firstOrNull { it.lifecycle() == FocusState.RELEASE_PENDING && it.endAt > now.wallMillis }?.releaseToken
        return FocusPresentation(packageName, end, ((end - now.wallMillis).coerceAtLeast(0) + 999) / 1000,
            token, token?.let { remaining(it, now) } ?: 0)
    }
    suspend fun beginRelease(): String? = core.execute { now ->
        val (projection, token) = store.transaction {
            val current = reconcile(now).sessions
            val existing = current.firstOrNull { it.lifecycle() == FocusState.RELEASE_PENDING }?.releaseToken
            val token = existing ?: current.takeIf { it.isNotEmpty() }?.let { UUID.randomUUID().toString() }
            if (token != null && existing == null) current.forEach {
                transition(it, it.copy(state = FocusState.RELEASE_PENDING.name, releaseToken = token,
                    waitStartedAt = now.wallMillis, waitElapsed = now.elapsedMillis, waitBoot = now.bootCount))
            }
            reconcile(now) to token
        }
        publish(projection); token
    }
    suspend fun cancelRelease(token: String) = core.execute { now ->
        publish(store.transaction {
            releaseSessions(token).filter { it.lifecycle() == FocusState.RELEASE_PENDING }.forEach {
                transition(it, it.copy(state = FocusState.ACTIVE.name, releaseToken = null,
                    waitElapsed = 0, waitStartedAt = 0, waitBoot = -1))
            }
            reconcile(now)
        })
    }
    suspend fun confirmRelease(token: String): Boolean = core.execute { now ->
        val (projection, accepted) = store.transaction {
            reconcile(now)
            val pending = releaseSessions(token).filter { it.lifecycle() == FocusState.RELEASE_PENDING && it.endAt > now.wallMillis }
            val accepted = pending.isNotEmpty() && pending.all {
                EmergencyWait.remaining(it.waitElapsed, it.waitBoot, now.elapsedMillis, now.bootCount) == 0L
            }
            if (accepted) pending.forEach {
                transition(it, it.copy(state = FocusState.EMERGENCY_RELEASED.name, releasedAt = now.wallMillis))
            }
            reconcile(now) to accepted
        }
        publish(projection); accepted
    }
}
