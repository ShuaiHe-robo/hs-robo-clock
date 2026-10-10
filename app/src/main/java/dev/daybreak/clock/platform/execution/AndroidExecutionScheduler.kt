package dev.daybreak.clock.platform.execution

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.daybreak.clock.MainActivity
import dev.daybreak.clock.domain.execution.*
import dev.daybreak.clock.platform.alarm.AlarmReceiver
import dev.daybreak.clock.platform.alarm.AlarmRingingService
import dev.daybreak.clock.platform.focus.FocusBoundaryReceiver

/** The purpose determines Android delivery/permission semantics, never the business state. */
class AndroidExecutionScheduler(private val context: Context) : ExecutionScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)
    override fun exactAllowed() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun trigger(key: WakeKey, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE): PendingIntent? =
        when (key.purpose) {
            WakePurpose.ALARM -> PendingIntent.getForegroundService(context, 0,
                Intent(context, AlarmRingingService::class.java).setAction("FIRE")
                    .setData(Uri.parse("daybreak://occurrence/${key.id}")).putExtra("occurrence", key.id), flags)
            WakePurpose.FOCUS_BOUNDARY -> PendingIntent.getBroadcast(context, 2002,
                Intent(context, FocusBoundaryReceiver::class.java).setAction("FOCUS_BOUNDARY")
                    .setData(Uri.parse("daybreak://focus/boundary")), flags)
        }
    private fun legacy(key: WakeKey): PendingIntent? = PendingIntent.getBroadcast(context,
        if (key.purpose == WakePurpose.ALARM) 0 else 2002,
        if (key.purpose == WakePurpose.ALARM) Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("daybreak://occurrence/${key.id}")) else Intent(context, FocusBoundaryReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    private fun remove(pending: PendingIntent?) { pending?.let { manager.cancel(it); it.cancel() } }
    override fun schedule(wake: ScheduledWake): ScheduleResult {
        try {
            val direct = requireNotNull(trigger(wake.key))
            var exact = exactAllowed()
            when (wake.key.purpose) {
                WakePurpose.ALARM -> {
                    if (!exact) return ScheduleResult("需要允许精确闹钟")
                    val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(wake.at, show), direct)
                }
                WakePurpose.FOCUS_BOUNDARY -> {
                    if (exact) try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wake.at, direct) }
                    catch (_: SecurityException) { exact = false }
                    if (!exact) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wake.at, direct)
                }
            }
            // Migrate only after a successful replacement; preserve the old entry on failure.
            remove(legacy(wake.key))
            return ScheduleResult(exact = exact)
        } catch (_: SecurityException) { return ScheduleResult("系统调度权限不可用") }
        catch (_: RuntimeException) { return ScheduleResult("系统排程失败，请重试") }
    }
    override fun cancel(key: WakeKey) {
        remove(trigger(key, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        remove(legacy(key))
    }
}
