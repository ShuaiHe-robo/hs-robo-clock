package dev.daybreak.clock.domain.execution

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class ExecutionTime(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int, val zone: ZoneId)
fun interface ExecutionClock { fun now(): ExecutionTime }

enum class RecoveryReason(val recomputeCalendar: Boolean = false) {
    PROCESS_START, APP_RESUME, BOOT, UNLOCK, PACKAGE_REPLACED, EXACT_PERMISSION,
    CLOCK_CHANGED(true), TIMEZONE_CHANGED(true)
}

enum class WakePurpose { ALARM, FOCUS_BOUNDARY }
data class WakeKey(val purpose: WakePurpose, val id: String) {
    companion object {
        fun alarm(id: String) = WakeKey(WakePurpose.ALARM, id)
        val focus = WakeKey(WakePurpose.FOCUS_BOUNDARY, "focus-boundary")
    }
}
data class ScheduledWake(val key: WakeKey, val at: Long)
data class ScheduleResult(val error: String? = null, val exact: Boolean = true) {
    val registered get() = error == null
}
interface ExecutionScheduler {
    fun exactAllowed(): Boolean
    fun schedule(wake: ScheduledWake): ScheduleResult
    fun cancel(key: WakeKey)
}

/** Shared command order, clock and system registration. Durable business states stay in their engines. */
class ExecutionCore(
    private val clock: ExecutionClock,
    private val scheduler: ExecutionScheduler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val registrations = mutableMapOf<WakeKey, Pair<Long, ScheduleResult>>()
    fun now() = clock.now()
    fun exactAllowed() = scheduler.exactAllowed()
    suspend fun <T> execute(command: suspend (ExecutionTime) -> T): T =
        withContext(dispatcher) { mutex.withLock { command(clock.now()) } }

    // Only called inside execute. A cached intent is not evidence that Android still owns it:
    // delivery and recovery explicitly invalidate/rearm; failures are never cached.
    fun schedule(key: WakeKey, at: Long, force: Boolean = false): ScheduleResult {
        registrations[key]?.takeIf { !force && it.first == at }?.let { return it.second }
        val result = scheduler.schedule(ScheduledWake(key, at))
        if (result.registered) registrations[key] = at to result else registrations.remove(key)
        return result
    }
    fun cancel(key: WakeKey) { scheduler.cancel(key); registrations.remove(key) }
    fun delivered(key: WakeKey) { registrations.remove(key) }
}
