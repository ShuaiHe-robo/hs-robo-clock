package dev.daybreak.clock.platform.alarm

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import dev.daybreak.clock.R
import dev.daybreak.clock.clock
import dev.daybreak.clock.data.RingingSession
import kotlinx.coroutines.*
import dev.daybreak.clock.domain.alarm.RingingController
import dev.daybreak.clock.domain.alarm.RingingOutput

class AlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audio: AlarmAudio
    private var currentId: String? = null
    private var wake: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private lateinit var controller: RingingController

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("ringing", "闹钟响铃", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "响铃与锁屏关闭入口"; setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        audio = AlarmAudio(this)
        controller = RingingController(clock.alarms, scope, object : RingingOutput {
            override fun render(session: RingingSession?, newlySelected: Boolean) = renderPlayback(session, newlySelected)
            override fun finish() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }, clock.alarmReliability)
        vibrator = getSystemService(Vibrator::class.java)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Daybreak:Ringing").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
        // Answer-based dismissal can last longer than ten minutes. Retain a bounded lock
        // while this user-visible service is alive; destruction always releases it.
        scope.launch { while (isActive) { delay(9 * 60 * 1000L); wake?.acquire(10 * 60 * 1000L) } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("occurrence")
        val snapshot = clock.alarms.sessions.value.firstOrNull { it.state == "RINGING" }
        // Start within the platform deadline. Full-screen UI and audio wait for validation,
        // so a cancelled occurrence delivered during an edit cannot ring or steal the screen.
        startForeground(1001, notification(snapshot, fullScreen = false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        clock.alarmReliability.event("SERVICE_FOREGROUND", id, intent?.action.orEmpty())
        controller.deliver(id)
        return START_STICKY
    }
    private fun renderPlayback(next: RingingSession?, changed: Boolean) {
        if (next == null) {
            audio.stop(); vibrator?.cancel(); currentId = null
            return
        }
        if (changed) {
            currentId = next.id
            audio.play(next.ringtoneUri, locked = !clock.unlocked(),
                started = { clock.scope.launch { clock.alarmReliability.event("AUDIO_STARTED", next.id) } },
                fallback = { clock.scope.launch { clock.alarmReliability.event("AUDIO_FALLBACK", next.id); clock.alarms.markFallback(next.id) } })
            vibrator?.cancel()
            if (next.vibrate) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 700), 0))
        }
        getSystemService(NotificationManager::class.java).notify(1001, notification(next, changed))
        if (changed) showRingingSurface(next)
    }
    private fun showRingingSurface(session: RingingSession) {
        // SAW is a documented background Activity launch exception. The same Activity
        // covers other apps and the keyguard without dismissing the device lock.
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                clock.alarmReliability.event("RINGING_SURFACE_REQUESTED", session.id)
            } catch (e: RuntimeException) {
                clock.alarmReliability.event("RINGING_SURFACE_FAILED", session.id, e.toString())
            }
        } else clock.alarmReliability.event("RINGING_SURFACE_NOTIFICATION", session.id, "Overlay permission unavailable")
    }
    private fun notification(session: RingingSession?, fullScreen: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "ringing").setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(session?.label?.takeIf { it.isNotBlank() } ?: "闹钟响铃")
            .setContentText(if (session?.mathUnlockEnabled == true) "点按进入，答对算术题关闭" else "点按进入关闭闹钟")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(!fullScreen)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setAutoCancel(false)
            .setContentIntent(open).apply { if (session != null) setFullScreenIntent(open, true) }.build()
    }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        clock.alarmReliability.event("SERVICE_DESTROYED", currentId)
        controller.close(); scope.cancel(); audio.stop(); vibrator?.cancel()
        if (wake?.isHeld == true) wake?.release()
        super.onDestroy()
    }
}
