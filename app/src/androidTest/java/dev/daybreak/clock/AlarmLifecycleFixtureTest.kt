package dev.daybreak.clock

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.platform.execution.AndroidExecutionScheduler
import dev.daybreak.clock.domain.execution.ScheduledWake
import dev.daybreak.clock.domain.execution.WakeKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit stages around externally controlled process death / Doze / reboot, emulator only. */
class AlarmLifecycleFixtureTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val app get() = instrumentation.targetContext.applicationContext as ClockApplication
    private val fixtureId = "alarm-lifecycle-fixture"

    @Test fun prepare(): Unit = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") && arguments.getString("phase") == "prepare")
        app.alarms.delete(fixtureId)
        app.alarms.save(Alarm(id = fixtureId, hour = 23, minute = 59, days = 0, label = "生命周期验证", vibrate = false))
        val session = app.bootStore.allSessions().single { it.alarmId == fixtureId && it.state == "PENDING" }
        val seconds = arguments.getString("seconds")?.toLong() ?: 45
        val due = session.copy(scheduledAt = System.currentTimeMillis() + seconds * 1000)
        app.bootStore.putSession(due)
        app.database.dao().saveRinging(due)
        assertNull(AndroidExecutionScheduler(app).schedule(ScheduledWake(WakeKey.alarm(due.id), due.scheduledAt)).error)
    }

    @Test fun verify(): Unit = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone") && arguments.getString("phase") == "verify")
        try {
            val session = app.bootStore.allSessions().last { it.alarmId == fixtureId && it.state == "RINGING" }
            assertTrue(app.alarmReliability.state.value.events.any { it.occurrence == session.id && it.stage == "AUDIO_STARTED" })
            assertFalse(app.database.dao().alarm(fixtureId)!!.enabled)
            assertTrue(app.alarms.dismiss(session.id, null))
            assertEquals("ENDED", app.bootStore.session(session.id)!!.state)
        } finally { app.alarms.delete(fixtureId) }
    }
}
