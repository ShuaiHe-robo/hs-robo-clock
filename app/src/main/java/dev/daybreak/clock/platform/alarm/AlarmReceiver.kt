package dev.daybreak.clock.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.daybreak.clock.clock
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("occurrence") ?: return
        val s = context.clock.bootStore.session(id) ?: return
        if (s.state != "PENDING" && s.state != "RINGING") return
        try {
            context.startForegroundService(Intent(context, AlarmRingingService::class.java).setAction("FIRE").putExtra("occurrence", id))
        } catch (e: RuntimeException) {
            android.util.Log.e("Daybreak", "Unable to launch ringing service", e)
        }
    }
}

class ScheduleRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.clock.scope.launch {
            try {
                context.clock.alarms.recover(intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED)
                if (context.clock.unlocked()) context.clock.focus.refresh()
            } finally { pending.finish() }
        }
    }
}
