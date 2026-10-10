package dev.daybreak.clock.platform.alarm

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import dev.daybreak.clock.domain.alarm.AlarmEvents

data class AlarmEvent(val at: Long, val stage: String, val occurrence: String?, val detail: String)
data class AlarmReliabilityState(
    val events: List<AlarmEvent> = emptyList(),
    val lastExitAt: Long = 0,
    val stoppedAt: Long = 0,
    val acknowledgedStopAt: Long = 0,
    val acknowledgedMissedAt: Long = 0
) {
    val showStopNotice get() = stoppedAt > acknowledgedStopAt
}

/** Bounded, local evidence. Diagnostic I/O must never prevent an alarm from ringing. */
class AlarmReliabilityStore(context: Context) : AlarmEvents {
    private val file = AtomicFile(File(context.createDeviceProtectedStorageContext().filesDir, "alarm-reliability.json"))
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()

    private fun read(): AlarmReliabilityState = try {
        val root = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val list = root.optJSONArray("events") ?: JSONArray()
        AlarmReliabilityState(events = (0 until list.length()).map { index ->
            val e = list.getJSONObject(index)
            AlarmEvent(e.getLong("at"), e.getString("stage"), e.optString("occurrence").takeIf { it.isNotEmpty() }, e.optString("detail"))
        }, lastExitAt = root.optLong("lastExitAt"), stoppedAt = root.optLong("stoppedAt"),
            acknowledgedStopAt = root.optLong("acknowledgedStopAt"), acknowledgedMissedAt = root.optLong("acknowledgedMissedAt"))
    } catch (_: java.io.FileNotFoundException) { AlarmReliabilityState() }
    catch (e: Exception) { Log.w("DaybreakAlarm", "Cannot read diagnostic history", e); AlarmReliabilityState() }

    @Synchronized override fun event(stage: String, occurrence: String?, detail: String) {
        val entry = AlarmEvent(System.currentTimeMillis(), stage, occurrence, detail.take(500))
        update(mutable.value.copy(events = (mutable.value.events + entry).takeLast(300)))
        Log.i("DaybreakAlarm", "$stage occurrence=$occurrence ${entry.detail}")
    }

    @Synchronized fun acknowledge(stoppedAt: Long, missedAt: Long) {
        update(mutable.value.copy(acknowledgedStopAt = maxOf(mutable.value.acknowledgedStopAt, stoppedAt),
            acknowledgedMissedAt = maxOf(mutable.value.acknowledgedMissedAt, missedAt)))
    }

    fun captureLastExits(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val exits = context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 16)
            recordExits(exits.sortedBy { it.timestamp }.map {
                ExitRecord(it.timestamp, it.reason, it.description.orEmpty())
            })
        } catch (e: RuntimeException) { Log.w("DaybreakAlarm", "Exit information unavailable", e) }
    }

    data class ExitRecord(val at: Long, val reason: Int, val description: String)
    @androidx.annotation.RequiresApi(30)
    @Synchronized fun recordExits(exits: List<ExitRecord>) {
        exits.filter { it.at > mutable.value.lastExitAt }.sortedBy { it.at }.forEach { exit ->
            // USER_REQUESTED also covers recents dismissal and instrumentation. The public
            // API has no force-stop subreason: present a risk hint, never blame the user.
            val detail = exit.description.lowercase()
            val stopped = exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED &&
                !detail.contains("remove task") && !detail.contains("instr")
            update(mutable.value.copy(lastExitAt = exit.at,
                stoppedAt = if (stopped) maxOf(mutable.value.stoppedAt, exit.at) else mutable.value.stoppedAt))
            event("PROCESS_EXIT", detail = "at=${exit.at} reason=${exit.reason} ${exit.description}")
        }
    }

    private fun update(value: AlarmReliabilityState) {
        mutable.value = value
        try {
            val root = JSONObject().put("lastExitAt", value.lastExitAt).put("stoppedAt", value.stoppedAt)
                .put("acknowledgedStopAt", value.acknowledgedStopAt).put("acknowledgedMissedAt", value.acknowledgedMissedAt)
                .put("events", JSONArray(value.events.map { e ->
                    JSONObject().put("at", e.at).put("stage", e.stage).put("occurrence", e.occurrence.orEmpty()).put("detail", e.detail)
                }))
            val stream = file.startWrite()
            try { stream.write(root.toString().toByteArray()); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        } catch (e: Exception) { Log.w("DaybreakAlarm", "Cannot persist diagnostic history", e) }
    }
}
