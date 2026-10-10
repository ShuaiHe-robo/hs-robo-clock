package dev.daybreak.clock

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.BootStore
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.domain.execution.RecoveryReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Drives persisted execution without starting audio or vibration. */
@RunWith(AndroidJUnit4::class)
class TimerExecutionTest {
    @Test fun pausedTimersRecoverAndStaleCallbacksCannotRingOrBypassMath() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
        app.alarms.startTimer(60_000, math = true, vibrate = false)
        val initial = app.alarms.timers.value.single()
        try {
            assertFalse(app.alarms.fire(initial.occurrenceId))
            app.alarms.pauseTimer(initial.id)
            val paused = app.bootStore.timer(initial.id)!!
            assertEquals("PAUSED", paused.state)
            assertTrue(paused.remaining in 1..60_000)
            assertFalse(app.alarms.fire(initial.occurrenceId))
            assertEquals(paused, BootStore(app).timer(initial.id))
            app.alarms.recover(RecoveryReason.CLOCK_CHANGED)
            assertEquals("PAUSED", app.bootStore.timer(initial.id)!!.state)
            app.alarms.resumeTimer(initial.id)
            val resumed = app.bootStore.timer(initial.id)!!
            assertNotEquals(initial.occurrenceId, resumed.occurrenceId)
            assertFalse(app.alarms.fire(initial.occurrenceId))
            // Advance the persisted deadline, then recover the actual AlarmManager entry.
            app.bootStore.putTimer(resumed.copy(endElapsed = SystemClock.elapsedRealtime() - 1, endAt = System.currentTimeMillis() - 1))
            app.alarms.recover(RecoveryReason.CLOCK_CHANGED)
            assertTrue(app.alarms.fire(resumed.occurrenceId))
            assertTrue(app.alarms.fire(resumed.occurrenceId))
            val ringing = app.bootStore.session(resumed.occurrenceId)!!
            app.alarms.cancelTimer(initial.id)
            assertEquals("RINGING", app.bootStore.timer(initial.id)!!.state)
            val challenge = MathChallenge(ringing.left, ringing.right, ringing.subtract)
            assertFalse(app.alarms.dismiss(ringing.id, (challenge.answer + 1).toString()))
            assertFalse(app.alarms.dismiss(ringing.id, null))
            assertTrue(app.alarms.dismiss(ringing.id, challenge.answer.toString()))
            assertEquals("ENDED", BootStore(app).timer(initial.id)!!.state)
            assertTrue(app.alarms.timers.value.isEmpty())
        } finally {
            val t = app.bootStore.timer(initial.id)!!
            if (t.state == "RINGING") {
                val s = app.bootStore.session(t.occurrenceId)!!
                app.alarms.dismiss(s.id, MathChallenge(s.left, s.right, s.subtract).answer.toString())
            } else app.alarms.cancelTimer(t.id)
        }
    }

    @Test fun cancelSurvivesRecoveryAndNormalTimerNeedsNoAnswer() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
        app.alarms.startTimer(60_000, false, false)
        val first = app.alarms.timers.value.single()
        app.alarms.cancelTimer(first.id)
        app.alarms.recover()
        assertFalse(app.alarms.fire(first.occurrenceId))
        assertEquals("CANCELLED", BootStore(app).timer(first.id)!!.state)
        app.alarms.startTimer(60_000, false, false)
        val next = app.alarms.timers.value.single()
        app.bootStore.putTimer(next.copy(endElapsed = SystemClock.elapsedRealtime() - 1))
        assertTrue(app.alarms.fire(next.occurrenceId))
        assertTrue(app.alarms.dismiss(next.occurrenceId, null))
    }
}
