package dev.daybreak.clock.domain.alarm

import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.data.RingingSession
import dev.daybreak.clock.data.TimerSession
import kotlinx.coroutines.flow.StateFlow

/** The durable execution journal is authoritative; unlocked database history is a projection. */
interface AlarmExecutionStore {
    val sessions: StateFlow<List<RingingSession>>
    val timers: StateFlow<List<TimerSession>>
    suspend fun synchronize()
    fun alarms(): List<Alarm>
    suspend fun alarm(id: String): Alarm?
    suspend fun saveAlarm(alarm: Alarm)
    suspend fun deleteAlarm(id: String)
    fun occurrences(): List<RingingSession>
    fun occurrence(id: String): RingingSession?
    fun timer(id: String): TimerSession?
    fun timerSnapshots(): List<TimerSession>
    suspend fun write(session: RingingSession, timer: TimerSession? = null)
    fun writeTimer(timer: TimerSession)
}

interface AlarmEvents {
    fun event(stage: String, occurrence: String? = null, detail: String = "")
}
object NoAlarmEvents : AlarmEvents { override fun event(stage: String, occurrence: String?, detail: String) = Unit }

enum class OccurrenceState {
    PENDING, RINGING, ENDED, CANCELLED, MISSED;
    fun permits(next: OccurrenceState) = this == next || when (this) {
        PENDING -> next in setOf(RINGING, CANCELLED, MISSED)
        RINGING -> next == ENDED
        else -> false
    }
}
// Preserve the v1 database/journal wire format while keeping execution transitions typed.
fun RingingSession.lifecycle() = OccurrenceState.entries.firstOrNull { it.name == state } ?: OccurrenceState.CANCELLED
