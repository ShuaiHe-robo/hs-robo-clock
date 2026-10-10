package dev.daybreak.clock.platform.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daybreak.clock.clock
import dev.daybreak.clock.ui.ClockTheme
import dev.daybreak.clock.ui.RingingScreen
import kotlinx.coroutines.launch

/** Shared global/lock-screen surface. Showing above the keyguard does not dismiss it. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        volumeControlStream = AudioManager.STREAM_ALARM
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        setContent {
            ClockTheme(dark = true) {
                val sessions by clock.alarms.sessions.collectAsStateWithLifecycle()
                val running = sessions.filter { it.state == "RINGING" }
                val session = running.firstOrNull()
                val scope = rememberCoroutineScope()
                LaunchedEffect(session?.id) {
                    if (session == null) finish()
                    else clock.alarmReliability.event("RINGING_SURFACE_VISIBLE", session.id,
                        "keyguard=${getSystemService(KeyguardManager::class.java).isKeyguardLocked}")
                }
                BackHandler { moveTaskToBack(true) }
                if (session != null) key(session.id) {
                    RingingScreen(session, running.size,
                        locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
                        dismiss = { answer, result ->
                            scope.launch {
                                try { result(clock.alarms.dismiss(session.id, answer)) }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) {
                                    clock.alarmReliability.event("DISMISS_FAILED", session.id, e.toString())
                                    result(false)
                                }
                            }
                        })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
}
