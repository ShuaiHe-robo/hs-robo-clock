package dev.daybreak.clock.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.daybreak.clock.MainActivity
import dev.daybreak.clock.data.RingingSession

class AndroidAlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun allowed() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun trigger(session: RingingSession) = PendingIntent.getBroadcast(context, 0,
        Intent(context, AlarmReceiver::class.java).setData(Uri.parse("daybreak://occurrence/${session.id}"))
            .putExtra("occurrence", session.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(session: RingingSession, at: Long = session.scheduledAt): String? {
        if (!allowed()) return "需要允许精确闹钟"
        return try {
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), trigger(session))
            null
        } catch (e: SecurityException) { "精确闹钟权限不可用" }
        catch (e: RuntimeException) { "系统排程失败，请重建计划" }
    }
    fun cancel(session: RingingSession) { manager.cancel(trigger(session)); trigger(session).cancel() }
}
