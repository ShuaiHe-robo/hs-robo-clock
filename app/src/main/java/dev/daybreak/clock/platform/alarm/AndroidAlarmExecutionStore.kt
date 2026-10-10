package dev.daybreak.clock.platform.alarm

import dev.daybreak.clock.ClockApplication
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.alarm.AlarmExecutionStore

/** All credential/device-protected storage knowledge stays at this seam. */
class AndroidAlarmExecutionStore(private val app: ClockApplication, private val journal: BootStore) : AlarmExecutionStore {
    override val sessions = journal.sessions
    override val timers = journal.timers
    override fun alarms() = journal.alarms()
    override fun occurrences() = journal.allSessions()
    override fun occurrence(id: String) = journal.session(id)
    override fun timer(id: String) = journal.timer(id)
    override fun timerSnapshots() = journal.allTimers()
    override fun writeTimer(timer: TimerSession) = journal.putTimer(timer)
    override suspend fun alarm(id: String) = if (app.unlocked()) app.database.dao().alarm(id) else journal.alarm(id)
    override suspend fun saveAlarm(alarm: Alarm) {
        if (app.unlocked()) app.database.dao().saveAlarm(alarm)
        journal.putAlarm(alarm)
    }
    override suspend fun deleteAlarm(id: String) {
        journal.removeAlarm(id)
        if (app.unlocked()) app.database.dao().deleteAlarm(id)
    }
    override suspend fun synchronize() {
        if (!app.unlocked()) return
        val dao = app.database.dao()
        journal.allSessions().forEach { dao.saveRinging(it) }
        journal.replaceAlarms(dao.alarms())
    }
    override suspend fun write(session: RingingSession, timer: TimerSession?) {
        if (timer == null) journal.putSession(session) else journal.putTimerSession(timer, session)
        if (app.unlocked()) {
            // A history projection failure cannot undo a durable transition or suppress audio.
            try { app.database.dao().saveRinging(session) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                app.alarmReliability.event("HISTORY_WRITE_FAILED", session.id, e.toString())
            }
        }
    }
}
