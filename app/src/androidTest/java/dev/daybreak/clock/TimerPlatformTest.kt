package dev.daybreak.clock

import android.app.NotificationManager
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.platform.alarm.AlarmActivity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TimerPlatformTest {
    /** Only run on the emulator started with -no-audio, never a physical phone. */
    @Test fun elapsedTimerStartsRealRingingAndRequiresCorrectAnswer() = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClockApplication
        val home = ActivityScenario.launch(MainActivity::class.java)
        app.alarms.startTimer(3000, true, false)
        val timer = app.alarms.timers.value.single()
        try {
            withTimeout(15_000) { while (app.bootStore.timer(timer.id)?.state != "RINGING") delay(100) }
            withTimeout(5000) { while (app.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 1001 }) delay(100) }
            val s = app.bootStore.session(timer.occurrenceId)!!
            val challenge = MathChallenge(s.left, s.right, s.subtract)
            assertFalse(app.alarms.dismiss(s.id, (challenge.answer + 1).toString()))
            val ringing = ActivityScenario.launch(AlarmActivity::class.java)
            try {
                delay(700)
                val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                val bitmap = requireNotNull(automation.takeScreenshot())
                File(app.getExternalFilesDir(null), "timer-ringing.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                assertTrue(app.alarms.dismiss(s.id, challenge.answer.toString()))
                withTimeout(5000) { while (app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1001 }) delay(100) }
            } finally { ringing.close() }
        } finally {
            val s = app.bootStore.session(timer.occurrenceId)!!
            if (s.state == "RINGING") app.alarms.dismiss(s.id, MathChallenge(s.left, s.right, s.subtract).answer.toString())
            else app.alarms.cancelTimer(timer.id)
            home.close()
        }
    }
}
