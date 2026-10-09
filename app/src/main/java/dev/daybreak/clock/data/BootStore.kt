package dev.daybreak.clock.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Minimal device-protected execution journal. Room stays credential-protected. */
class BootStore(context: Context) {
    private val file = AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir, "alarm-journal.json"))
    private val alarmMap = linkedMapOf<String, Alarm>()
    private val sessionMap = linkedMapOf<String, RingingSession>()
    private val timerMap = linkedMapOf<String, TimerSession>()
    private val mutableTimers = MutableStateFlow<List<TimerSession>>(emptyList())
    val timers = mutableTimers.asStateFlow()
    private val mutableSessions = MutableStateFlow<List<RingingSession>>(emptyList())
    val sessions = mutableSessions.asStateFlow()

    init {
        if (file.baseFile.exists()) {
            val root = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            val alarms = root.getJSONArray("alarms")
            repeat(alarms.length()) { val a = alarms.getJSONObject(it).alarm(); alarmMap[a.id] = a }
            val sessions = root.getJSONArray("sessions")
            repeat(sessions.length()) { val s = sessions.getJSONObject(it).session(); sessionMap[s.id] = s }
            root.optJSONArray("timers")?.let { timers ->
                repeat(timers.length()) {
                    val t = timers.getJSONObject(it)
                    val timer = TimerSession(t.getString("id"), t.getString("occurrence"), t.getLong("duration"),
                        t.getLong("remaining"), t.getLong("elapsed"), t.getLong("end"), t.getInt("boot"),
                        t.getBoolean("math"), t.getBoolean("vibrate"), t.getString("state"))
                    timerMap[timer.id] = timer
                }
            }
        }
        publish()
    }
    @Synchronized fun alarms() = alarmMap.values.toList()
    @Synchronized fun alarm(id: String) = alarmMap[id]
    @Synchronized fun allSessions() = sessionMap.values.toList()
    @Synchronized fun session(id: String) = sessionMap[id]
    @Synchronized fun putAlarm(alarm: Alarm) { alarmMap[alarm.id] = alarm; persist() }
    @Synchronized fun replaceAlarms(alarms: List<Alarm>) { alarmMap.clear(); alarms.forEach { alarmMap[it.id] = it }; persist() }
    @Synchronized fun removeAlarm(id: String) { alarmMap.remove(id); persist() }
    @Synchronized fun putSession(session: RingingSession) { sessionMap[session.id] = session; persist() }
    @Synchronized fun timer(id: String) = timerMap[id]
    @Synchronized fun allTimers() = timerMap.values.toList()
    @Synchronized fun putTimer(timer: TimerSession) { timerMap[timer.id] = timer; persist() }
    @Synchronized fun putTimerSession(timer: TimerSession, session: RingingSession) {
        timerMap[timer.id] = timer; sessionMap[session.id] = session; persist()
    }

    private fun publish() {
        mutableSessions.value = sessionMap.values.sortedBy { it.scheduledAt }
        mutableTimers.value = timerMap.values.filter { it.state in listOf("RUNNING", "PAUSED", "RINGING") }
    }
    private fun persist() {
        val root = JSONObject().put("alarms", JSONArray(alarmMap.values.map { it.json() }))
            .put("sessions", JSONArray(sessionMap.values.map { it.json() }))
            .put("timers", JSONArray(timerMap.values.map { t ->
                JSONObject().put("id", t.id).put("occurrence", t.occurrenceId).put("duration", t.duration)
                    .put("remaining", t.remaining).put("elapsed", t.endElapsed).put("end", t.endAt)
                    .put("boot", t.boot).put("math", t.mathUnlockEnabled).put("vibrate", t.vibrate).put("state", t.state)
            }))
        val stream = file.startWrite()
        try { stream.write(root.toString().toByteArray()); file.finishWrite(stream); publish() }
        catch (t: Throwable) { file.failWrite(stream); throw t }
    }
}

private fun JSONObject.nullString(key: String) = if (isNull(key)) null else getString(key)
private fun Alarm.json() = JSONObject().put("id", id).put("hour", hour).put("minute", minute).put("days", days)
    .put("enabled", enabled).put("label", label).put("uri", ringtoneUri ?: JSONObject.NULL)
    .put("source", ringtoneSource).put("name", ringtoneDisplayName).put("vibrate", vibrate)
    .put("math", mathUnlockEnabled).put("revision", revision)
private fun JSONObject.alarm() = Alarm(id = getString("id"), hour = getInt("hour"), minute = getInt("minute"),
    days = getInt("days"), enabled = getBoolean("enabled"), label = getString("label"), ringtoneUri = nullString("uri"),
    ringtoneSource = getString("source"), ringtoneDisplayName = getString("name"), vibrate = getBoolean("vibrate"),
    mathUnlockEnabled = getBoolean("math"), revision = getLong("revision"))
private fun RingingSession.json() = JSONObject().put("id", id).put("alarmId", alarmId).put("revision", revision)
    .put("scheduledAt", scheduledAt).put("hour", hour).put("minute", minute).put("days", days).put("label", label)
    .put("uri", ringtoneUri ?: JSONObject.NULL).put("source", ringtoneSource).put("name", ringtoneDisplayName)
    .put("vibrate", vibrate).put("math", mathUnlockEnabled).put("left", left).put("right", right).put("subtract", subtract)
    .put("state", state).put("error", scheduleError ?: JSONObject.NULL).put("fallback", fallbackUsed)
private fun JSONObject.session() = RingingSession(id = getString("id"), alarmId = getString("alarmId"),
    revision = getLong("revision"), scheduledAt = getLong("scheduledAt"), hour = getInt("hour"), minute = getInt("minute"),
    days = getInt("days"), label = getString("label"), ringtoneUri = nullString("uri"), ringtoneSource = getString("source"),
    ringtoneDisplayName = getString("name"), vibrate = getBoolean("vibrate"), mathUnlockEnabled = getBoolean("math"),
    left = getInt("left"), right = getInt("right"), subtract = getBoolean("subtract"), state = getString("state"),
    scheduleError = nullString("error"), fallbackUsed = optBoolean("fallback"))
