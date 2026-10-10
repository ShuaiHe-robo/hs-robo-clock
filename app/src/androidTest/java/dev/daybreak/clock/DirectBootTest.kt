package dev.daybreak.clock

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.platform.execution.AndroidExecutionScheduler
import dev.daybreak.clock.domain.execution.ScheduledWake
import dev.daybreak.clock.domain.execution.WakeKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two explicit stages around an emulator reboot. Not part of the normal regression run. */
@RunWith(AndroidJUnit4::class)
class DirectBootTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
    @Test fun prepareBoot(): Unit = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") && InstrumentationRegistry.getArguments().getString("phase") == "prepare")
        val alarm = Alarm(id = "direct-boot-fixture", label = "解锁前静默测试", days = 0, mathUnlockEnabled = true,
            ringtoneUri = "content://dev.daybreak.missing/locked-file", ringtoneSource = "FILE", ringtoneDisplayName = "解锁前不可读文件")
        app.alarms.save(alarm)
        val session = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
        val due = session.copy(scheduledAt = System.currentTimeMillis() + 90_000)
        app.bootStore.putSession(due); app.database.dao().saveRinging(due)
        assertNull(AndroidExecutionScheduler(app).schedule(ScheduledWake(WakeKey.alarm(due.id), due.scheduledAt)).error)
        android.util.Log.i("BootFixture", "Prepared occurrence ${due.id} at ${due.scheduledAt}; question ${due.left},${due.right},${due.subtract}")
    }
    @Test fun verifyAfterUnlock() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") && InstrumentationRegistry.getArguments().getString("phase") == "verify")
        val session = app.bootStore.allSessions().last { it.alarmId == "direct-boot-fixture" && it.state == "ENDED" }
        assertTrue(app.unlocked())
        val expected = InstrumentationRegistry.getArguments().getString("answer")
        assertEquals(expected, MathChallenge(session.left, session.right, session.subtract).answer.toString())
        app.alarms.recover()
        assertFalse(app.database.dao().alarm("direct-boot-fixture")!!.enabled)
        app.alarms.delete("direct-boot-fixture")
        assertEquals("ENDED", app.bootStore.session(session.id)!!.state)
    }
}
