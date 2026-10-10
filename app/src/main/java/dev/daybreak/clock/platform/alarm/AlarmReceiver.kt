package dev.daybreak.clock.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.daybreak.clock.clock
import dev.daybreak.clock.domain.execution.RecoveryReason
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("occurrence") ?: return
        context.clock.alarmReliability.event("LEGACY_TRIGGER", id)
        val s = context.clock.bootStore.session(id) ?: return
        if (s.state != "PENDING" && s.state != "RINGING") return
        try {
            context.startForegroundService(Intent(context, AlarmRingingService::class.java).setAction("FIRE").putExtra("occurrence", id))
        } catch (e: RuntimeException) {
            context.clock.alarmReliability.event("SERVICE_START_FAILED", id, e.toString())
            android.util.Log.e("Daybreak", "Unable to launch ringing service", e)
        }
    }
}

class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.clock.scope.launch {
            try {
                context.clock.recoverSchedules(when (intent.action) {
                    Intent.ACTION_TIME_CHANGED -> RecoveryReason.CLOCK_CHANGED
                    Intent.ACTION_TIMEZONE_CHANGED -> RecoveryReason.TIMEZONE_CHANGED
                    Intent.ACTION_USER_UNLOCKED -> RecoveryReason.UNLOCK
                    Intent.ACTION_MY_PACKAGE_REPLACED -> RecoveryReason.PACKAGE_REPLACED
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> RecoveryReason.EXACT_PERMISSION
                    else -> RecoveryReason.BOOT
                })
            } finally { pending.finish() }
        }
    }
}
