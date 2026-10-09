package dev.daybreak.clock.platform.alarm

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import dev.daybreak.clock.R
import dev.daybreak.clock.clock
import dev.daybreak.clock.data.RingingSession
import kotlinx.coroutines.*

class AlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var audio: AlarmAudio
    private var currentId: String? = null
    private var wake: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var collector: Job? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("ringing", "闹钟响铃", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "响铃与锁屏关闭入口"; setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        audio = AlarmAudio(this)
        vibrator = getSystemService(Vibrator::class.java)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Daybreak:Ringing").apply { acquire(10 * 60 * 1000L) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("occurrence")
        val snapshot = id?.let { clock.bootStore.session(it) }
            ?: clock.bootStore.allSessions().firstOrNull { it.state == "RINGING" }
        startForeground(1001, notification(snapshot, fullScreen = currentId == null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        scope.launch {
            if (intent?.action == "DISMISS" && id != null) clock.alarms.dismiss(id, intent.getStringExtra("answer"))
            else if (id != null) clock.alarms.fire(id)
            if (collector == null) collector = scope.launch {
                clock.alarms.sessions.collect { sessions ->
                    val next = sessions.firstOrNull { it.state == "RINGING" }
                    if (next == null) { audio.stop(); vibrator?.cancel(); currentId = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                    else if (next.id != currentId) {
                        currentId = next.id
                        audio.play(next.ringtoneUri, locked = !clock.unlocked()) { clock.scope.launch { clock.alarms.markFallback(next.id) } }
                        vibrator?.cancel()
                        if (next.vibrate) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 700), 0))
                        getSystemService(NotificationManager::class.java).notify(1001, notification(next, true))
                    }
                }
            }
        }
        return START_STICKY
    }
    private fun notification(session: RingingSession?, fullScreen: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "ringing").setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(session?.label?.takeIf { it.isNotBlank() } ?: "闹钟响铃")
            .setContentText(if (session?.mathUnlockEnabled == true) "点按进入，答对算术题关闭" else "点按进入关闭闹钟")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setAutoCancel(false)
            .setContentIntent(open).apply { if (fullScreen) setFullScreenIntent(open, true) }.build()
    }
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() {
        scope.cancel(); audio.stop(); vibrator?.cancel()
        if (wake?.isHeld == true) wake?.release()
        super.onDestroy()
    }
}
