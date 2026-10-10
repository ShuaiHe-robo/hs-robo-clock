package dev.daybreak.clock.domain.execution

import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.domain.alarm.AlarmEngine
import dev.daybreak.clock.domain.alarm.MemoryExecutionStore
import dev.daybreak.clock.domain.focus.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExecutionCoreTest {
    @Test fun bothEnginesShareRegistrationWithoutCancellingEachOthersWakeups() = runTest {
        val f = FocusFixture()
        val alarms = AlarmEngine(MemoryExecutionStore(), f.core)
        alarms.save(Alarm(id = "morning", hour = 9, minute = 30, days = 127))
        f.engine.save(f.rule(end = 9 * 60 + 30))
        val occurrence = alarms.sessions.value.single()
        assertEquals(setOf(WakeKey.focus, WakeKey.alarm(occurrence.id)), f.scheduler.wakes.keys)
        f.engine.delete("rule")
        assertEquals(2, f.scheduler.wakes.size) // Frozen focus snapshot survives source deletion.
        f.clock.advance(30 * 60_000)
        f.engine.boundaryDelivered()
        assertEquals(setOf(WakeKey.alarm(occurrence.id)), f.scheduler.wakes.keys)
        assertTrue(alarms.fire(occurrence.id))
        assertTrue(f.scheduler.wakes.keys.none { it == WakeKey.alarm(occurrence.id) })
    }
    @Test fun successfulBoundaryIsCachedUntilDeliveryOrExplicitRecovery() = runTest {
        val f = FocusFixture()
        f.engine.save(f.rule())
        val requests = f.scheduler.registrations
        repeat(100) { f.engine.refresh() }
        assertEquals(requests, f.scheduler.registrations)
        f.engine.boundaryDelivered()
        assertEquals(requests + 1, f.scheduler.registrations)
        f.engine.recover(RecoveryReason.APP_RESUME)
        assertEquals(requests + 2, f.scheduler.registrations)
    }
    @Test fun commandsUseOneClockSampleAndAreSerializedAcrossCallers() = runTest {
        val f = FocusFixture()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch { f.core.execute { time ->
            order += "first"; entered.complete(Unit); release.await()
            assertEquals(100_000L, time.elapsedMillis); order += "committed"
        } }
        entered.await()
        val second = launch { f.core.execute { time -> order += "second"; assertEquals(101_000L, time.elapsedMillis) } }
        f.clock.advance(1000)
        assertEquals(listOf("first"), order)
        release.complete(Unit); first.join(); second.join()
        assertEquals(listOf("first", "committed", "second"), order)
    }
}
