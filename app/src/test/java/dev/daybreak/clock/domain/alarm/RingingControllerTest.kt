package dev.daybreak.clock.domain.alarm

import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.data.RingingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingingControllerTest {
    @Test fun duplicateAndRejectedDeliveriesDoNotRestartSoundOrFinishActiveSession() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15, days = 0, mathUnlockEnabled = false)
        f.engine.save(alarm)
        val due = f.pending(alarm.id)
        f.clock.advance(75 * 60_000)
        val output = RecordingOutput()
        val controller = RingingController(f.engine, this, output)
        try {
            controller.deliver("deleted-occurrence")
            controller.deliver(due.id)
            advanceUntilIdle()
            repeat(100) { controller.deliver(due.id) }
            controller.deliver("deleted-occurrence")
            advanceUntilIdle()
            assertEquals(listOf(due.id), output.started)
            assertEquals(0, output.finished)
            assertTrue(f.engine.dismiss(due.id, null))
            advanceUntilIdle()
            assertNull(output.current)
            assertEquals(1, output.finished)
        } finally { controller.close() }
    }

    @Test fun simultaneousAlarmsArePlayedInOrderAndCloseReleasesOutput() = runTest {
        val f = AlarmFixture()
        val first = Alarm(hour = 8, minute = 15, days = 0)
        val second = first.copy(id = "second-alarm")
        f.engine.save(first); f.engine.save(second)
        val a = f.pending(first.id); val b = f.pending(second.id)
        f.clock.advance(75 * 60_000)
        val output = RecordingOutput()
        val controller = RingingController(f.engine, this, output)
        try {
            controller.deliver(a.id); controller.deliver(b.id)
            advanceUntilIdle()
            assertEquals(listOf(a.id), output.started)
            f.engine.dismiss(a.id, null)
            advanceUntilIdle()
            assertEquals(listOf(a.id, b.id), output.started)
            assertEquals(0, output.finished)
            controller.close()
            assertNull(output.current)
            assertEquals("RINGING", f.store.occurrence(b.id)!!.state)
        } finally { controller.close() }
    }

    private class RecordingOutput : RingingOutput {
        val started = mutableListOf<String>()
        var current: String? = null
        var finished = 0
        override fun render(session: RingingSession?, newlySelected: Boolean) {
            current = session?.id
            if (session != null && newlySelected) started += session.id
        }
        override fun finish() { finished++ }
    }
}
