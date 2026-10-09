package dev.daybreak.clock

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.platform.alarm.AndroidAlarmScheduler
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on the emulator launched with -no-audio; never rings a physical device. */
@RunWith(AndroidJUnit4::class)
class SilentPlatformTest {
    @Test fun exactCallbackStartsForegroundServiceAndUnreadableRingtoneFallsBack() = runBlocking {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
        assumeTrue(AndroidAlarmScheduler(app).allowed())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val alarm = Alarm(label = "无声平台测试", days = 0, mathUnlockEnabled = true,
            ringtoneUri = "content://dev.daybreak.missing/no-file", ringtoneSource = "FILE", ringtoneDisplayName = "失效文件")
        try {
            app.alarms.save(alarm)
            val pending = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
            val due = pending.copy(scheduledAt = System.currentTimeMillis() + 3000)
            app.bootStore.putSession(due)
            app.database.dao().saveRinging(due)
            assertNull(AndroidAlarmScheduler(app).schedule(due))
            withTimeout(15_000) { while (app.bootStore.session(due.id)?.state != "RINGING") delay(100) }
            withTimeout(15_000) { while (app.bootStore.session(due.id)?.fallbackUsed != true) delay(100) }
            assertTrue(app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1001 })
            val q = MathChallenge(due.left, due.right, due.subtract)
            assertFalse(app.alarms.dismiss(due.id, (q.answer + 1).toString()))
            assertEquals("RINGING", app.bootStore.session(due.id)!!.state)
            // The snapshot survives deleting its source and cannot be replaced by UI edits.
            app.alarms.delete(alarm.id)
            assertTrue(app.alarms.dismiss(due.id, q.answer.toString()))
            withTimeout(5000) { while (app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1001 }) delay(100) }
        } finally {
            app.alarms.delete(alarm.id)
            app.bootStore.allSessions().filter { it.alarmId == alarm.id && it.state == "RINGING" }.forEach {
                app.alarms.dismiss(it.id, MathChallenge(it.left, it.right, it.subtract).answer.toString())
            }
            scenario.close()
        }
    }
}
