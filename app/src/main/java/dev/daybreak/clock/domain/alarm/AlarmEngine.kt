package dev.daybreak.clock.domain.alarm

import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.domain.TimeRules
import dev.daybreak.clock.domain.TimerRules
import dev.daybreak.clock.domain.execution.*
import java.time.Instant
import java.util.UUID

/** Owns every occurrence transition. Android entry points only deliver IDs or recovery reasons. */
class AlarmEngine(
    private val store: AlarmExecutionStore,
    private val core: ExecutionCore,
    private val events: AlarmEvents = NoAlarmEvents
) {
    val sessions = store.sessions
    val timers = store.timers
    private suspend fun <T> execute(block: suspend (ExecutionTime) -> T): T = core.execute(block)
    private fun remaining(timer: TimerSession, now: ExecutionTime) = timer.remainingAt(now.bootCount, now.elapsedMillis, now.wallMillis)

    private suspend fun transition(session: RingingSession, state: OccurrenceState, timer: TimerSession? = null) {
        check(session.lifecycle().permits(state)) { "Invalid occurrence transition ${session.state} -> $state" }
        val next = session.copy(state = state.name, scheduleError = if (state == OccurrenceState.RINGING) null else session.scheduleError)
        // Persist before cancelling delivery or starting any user-visible effects.
        store.write(next, timer)
        core.cancel(WakeKey.alarm(next.id))
        events.event(state.name, next.id, "scheduledAt=${next.scheduledAt}")
    }
    private suspend fun register(session: RingingSession, at: Long = session.scheduledAt): String? {
        store.write(session.copy(scheduleError = "正在排程"))
        events.event("SCHEDULE_REQUEST", session.id, at.toString())
        val error = core.schedule(WakeKey.alarm(session.id), at, force = true).error
        store.write(session.copy(scheduleError = error))
        events.event(if (error == null) "SCHEDULED" else "SCHEDULE_FAILED", session.id, error ?: at.toString())
        return error
    }
    private suspend fun invalidate(alarmId: String) {
        store.occurrences().filter { it.alarmId == alarmId && it.lifecycle() == OccurrenceState.PENDING }
            .forEach { transition(it, OccurrenceState.CANCELLED) }
    }
    private suspend fun scheduleNext(alarm: Alarm, now: ExecutionTime) {
        if (!alarm.enabled) return
        val at = TimeRules.nextAlarm(alarm.hour, alarm.minute, alarm.days, Instant.ofEpochMilli(now.wallMillis), now.zone).toEpochMilli()
        if (store.occurrences().any { it.alarmId == alarm.id && it.revision == alarm.revision && it.lifecycle() == OccurrenceState.PENDING && it.scheduledAt == at }) return
        val question = MathChallenge.generate()
        register(RingingSession(UUID.randomUUID().toString(), alarm.id, alarm.revision, at, alarm.hour,
            alarm.minute, alarm.days, alarm.label, alarm.ringtoneUri, alarm.ringtoneSource, alarm.ringtoneDisplayName,
            alarm.vibrate, alarm.mathUnlockEnabled, question.left, question.right, question.subtract))
    }
    suspend fun save(alarm: Alarm) = execute { now ->
        require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.days in 0..127)
        val priorGeneration = maxOf(store.alarm(alarm.id)?.revision ?: 0L,
            store.occurrences().filter { it.alarmId == alarm.id }.maxOfOrNull { it.revision } ?: 0L)
        val next = alarm.copy(revision = priorGeneration + 1)
        invalidate(alarm.id)
        store.saveAlarm(next)
        scheduleNext(next, now)
    }
    suspend fun delete(id: String) = execute { invalidate(id); store.deleteAlarm(id) }

    suspend fun startTimer(duration: Long, math: Boolean, vibrate: Boolean) = execute { now ->
        require(duration in 1000..TimerRules.MAX_DURATION) { "请设置 1 秒到 24 小时的时长" }
        require(store.timerSnapshots().none { it.state in listOf("RUNNING", "PAUSED", "RINGING") }) { "请先结束当前计时器" }
        require(core.exactAllowed()) { "请先允许精确闹钟，再开始计时" }
        scheduleTimer(TimerSession(duration = duration, mathUnlockEnabled = math, vibrate = vibrate), now)
    }
    private suspend fun scheduleTimer(timer: TimerSession, now: ExecutionTime) {
        val running = timer.copy(state = "RUNNING", endAt = now.wallMillis + timer.remaining,
            endElapsed = now.elapsedMillis + timer.remaining, boot = now.bootCount)
        val time = Instant.ofEpochMilli(running.endAt).atZone(now.zone)
        val question = MathChallenge.generate()
        val session = RingingSession(running.occurrenceId, "timer:${running.id}", 1, running.endAt,
            time.hour, time.minute, 0, "计时结束", null, "DEFAULT", "默认闹钟铃声", running.vibrate,
            running.mathUnlockEnabled, question.left, question.right, question.subtract)
        store.write(session, running)
        val error = register(session)
        if (error != null) throw IllegalStateException(error)
    }
    suspend fun pauseTimer(id: String) = execute { now ->
        val timer = store.timer(id) ?: return@execute
        if (timer.state != "RUNNING") return@execute
        val left = remaining(timer, now)
        require(left > 0) { "计时已结束，请进入响铃页面关闭" }
        val session = store.occurrence(timer.occurrenceId) ?: return@execute
        transition(session, OccurrenceState.CANCELLED, timer.copy(state = "PAUSED", remaining = left))
    }
    suspend fun resumeTimer(id: String) = execute { now ->
        val timer = store.timer(id) ?: return@execute
        if (timer.state != "PAUSED") return@execute
        require(core.exactAllowed()) { "请先允许精确闹钟，再继续计时" }
        scheduleTimer(timer.copy(occurrenceId = UUID.randomUUID().toString()), now)
    }
    suspend fun cancelTimer(id: String) = execute { now ->
        val timer = store.timer(id) ?: return@execute
        if (timer.state !in listOf("RUNNING", "PAUSED")) return@execute
        require(timer.state != "RUNNING" || remaining(timer, now) > 0) { "计时已结束，请进入响铃页面关闭" }
        val session = store.occurrence(timer.occurrenceId) ?: return@execute
        transition(session, OccurrenceState.CANCELLED, timer.copy(state = "CANCELLED"))
    }

    suspend fun fire(id: String): Boolean = execute { now ->
        events.event("FIRE_RECEIVED", id)
        val session = store.occurrence(id) ?: return@execute false
        if (session.lifecycle() == OccurrenceState.RINGING) return@execute true
        if (session.lifecycle() != OccurrenceState.PENDING) return@execute false
        if (session.isTimer) {
            val timer = store.timer(session.alarmId.removePrefix("timer:")) ?: return@execute false
            if (timer.state != "RUNNING" || timer.occurrenceId != id) return@execute false
            val left = remaining(timer, now)
            if (left > 0) { register(session.copy(scheduledAt = now.wallMillis + left)); return@execute false }
            transition(session, OccurrenceState.RINGING, timer.copy(state = "RINGING", remaining = 0))
            return@execute true
        }
        if (session.scheduledAt > now.wallMillis + EARLY_TOLERANCE) return@execute false
        val alarm = store.alarm(session.alarmId)
        if (alarm == null || !alarm.enabled || alarm.revision != session.revision) {
            transition(session, OccurrenceState.CANCELLED); return@execute false
        }
        if (session.scheduledAt < now.wallMillis - LATE_GRACE) {
            transition(session, OccurrenceState.MISSED)
            if (alarm.days == 0) store.saveAlarm(alarm.copy(enabled = false)) else scheduleNext(alarm, now)
            return@execute false
        }
        transition(session, OccurrenceState.RINGING)
        if (alarm.days == 0) store.saveAlarm(alarm.copy(enabled = false)) else scheduleNext(alarm, now)
        true
    }
    suspend fun dismiss(id: String, answer: String?): Boolean = execute {
        val session = store.occurrence(id) ?: return@execute false
        if (session.lifecycle() != OccurrenceState.RINGING) return@execute false
        if (session.mathUnlockEnabled && !MathChallenge(session.left, session.right, session.subtract).accepts(answer.orEmpty())) return@execute false
        val timer = if (session.isTimer) store.timer(session.alarmId.removePrefix("timer:")) else null
        transition(session, OccurrenceState.ENDED, timer?.copy(state = "ENDED"))
        true
    }
    suspend fun markFallback(id: String) = execute {
        store.occurrence(id)?.takeIf { it.lifecycle() == OccurrenceState.RINGING }?.let { store.write(it.copy(fallbackUsed = true)) }
    }

    suspend fun recover(reason: RecoveryReason = RecoveryReason.APP_RESUME) = execute { now ->
        events.event("RECOVERY_STARTED", detail = reason.name)
        store.synchronize()
        // Consumed one-shots never become enabled again when the unlocked projection returns.
        store.alarms().filter { alarm -> alarm.enabled && store.occurrences().any {
            it.alarmId == alarm.id && it.revision == alarm.revision && it.days == 0 &&
                it.lifecycle() in setOf(OccurrenceState.RINGING, OccurrenceState.ENDED, OccurrenceState.MISSED)
        } }.forEach { store.saveAlarm(it.copy(enabled = false)) }
        store.occurrences().filter { it.lifecycle() == OccurrenceState.PENDING }.forEach { session ->
            if (session.isTimer) {
                val timer = store.timer(session.alarmId.removePrefix("timer:"))
                if (timer == null || timer.state != "RUNNING" || timer.occurrenceId != session.id) transition(session, OccurrenceState.CANCELLED)
                else {
                    val left = remaining(timer, now)
                    val at = now.wallMillis + left
                    store.writeTimer(timer.copy(endAt = at, endElapsed = now.elapsedMillis + left, boot = now.bootCount))
                    register(session.copy(scheduledAt = at), maxOf(at, now.wallMillis + RECOVERY_DELAY))
                }
                return@forEach
            }
            val alarm = store.alarm(session.alarmId)
            when {
                alarm == null || !alarm.enabled || alarm.revision != session.revision -> transition(session, OccurrenceState.CANCELLED)
                reason.recomputeCalendar && session.scheduledAt > now.wallMillis -> transition(session, OccurrenceState.CANCELLED)
                session.scheduledAt < now.wallMillis - LATE_GRACE -> {
                    transition(session, OccurrenceState.MISSED)
                    if (alarm.days == 0) store.saveAlarm(alarm.copy(enabled = false))
                }
                else -> register(session, maxOf(session.scheduledAt, now.wallMillis + RECOVERY_DELAY))
            }
        }
        // Recovery only registers system deliveries. Playback always starts at the delivery seam.
        store.occurrences().filter { it.lifecycle() == OccurrenceState.RINGING }.forEach { register(it, now.wallMillis + RECOVERY_DELAY) }
        store.alarms().filter { it.enabled }.forEach { alarm ->
            if (store.occurrences().none { it.alarmId == alarm.id && it.revision == alarm.revision && it.lifecycle() == OccurrenceState.PENDING }) scheduleNext(alarm, now)
        }
        events.event("RECOVERY_FINISHED", detail = reason.name)
    }
    companion object {
        const val LATE_GRACE = 10 * 60 * 1000L
        const val EARLY_TOLERANCE = 2000L
        const val RECOVERY_DELAY = 1500L
    }
}
