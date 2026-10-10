package dev.daybreak.clock.domain.alarm

import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.execution.*
import dev.daybreak.clock.domain.MathChallenge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Exercises the same execution interface as Android, without a process, sleep or real clock. */
class AlarmEngineTest {
    @Test fun recreatingDeletedAlarmCannotConsumeItsNewOccurrenceFromOldHistory() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15, days = 0)
        f.engine.save(alarm)
        val old = f.pending(alarm.id)
        f.clock.advance(75 * 60_000)
        assertTrue(f.engine.fire(old.id))
        assertTrue(f.engine.dismiss(old.id, null))
        f.engine.delete(alarm.id)
        f.engine.save(alarm)
        val fresh = f.pending(alarm.id)
        f.engine.recover()
        assertTrue("Past one-shot history must not consume a newly created generation", f.store.alarm(alarm.id)!!.enabled)
        assertTrue(fresh.revision > old.revision)
        assertEquals(fresh.id, f.pending(alarm.id).id)
        assertFalse(f.engine.fire(old.id))
    }
    @Test fun editingFencesOldCallbacksAndRingingSnapshotSurvivesDeletion() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15, days = 0, mathUnlockEnabled = true)
        f.engine.save(alarm)
        val old = f.pending(alarm.id)
        f.engine.save(alarm.copy(minute = 16))
        assertFalse(f.engine.fire(old.id))
        assertEquals("CANCELLED", f.store.occurrence(old.id)!!.state)
        assertFalse(old.id in f.scheduler.deliveries)
        val next = f.pending(alarm.id)
        f.clock.advance(76 * 60_000)
        assertTrue(f.engine.fire(next.id))
        assertTrue(f.engine.fire(next.id))
        assertFalse(f.store.alarm(alarm.id)!!.enabled)
        val question = MathChallenge(next.left, next.right, next.subtract)
        assertFalse(f.engine.dismiss(next.id, null))
        assertFalse(f.engine.dismiss(next.id, (question.answer + 1).toString()))
        f.engine.delete(alarm.id)
        assertEquals("RINGING", f.store.occurrence(next.id)!!.state)
        assertTrue(f.engine.dismiss(next.id, question.answer.toString()))
        assertFalse(f.engine.fire(next.id))
        assertEquals("ENDED", f.store.occurrence(next.id)!!.state)
    }

    @Test fun processRecreationReusesDurableOccurrenceAndRecoveryIsIdempotent() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15, days = 127, mathUnlockEnabled = true)
        f.engine.save(alarm)
        val pending = f.pending(alarm.id)
        // AlarmManager can be empty after reboot, upgrade or a stopped package was reopened.
        f.scheduler.deliveries.clear()
        val recreated = AlarmEngine(f.store, ExecutionCore(f.clock, f.scheduler, Dispatchers.Unconfined))
        repeat(3) { recreated.recover(RecoveryReason.BOOT) }
        assertEquals(listOf(pending), recreated.sessions.value)
        assertEquals(mapOf(pending.id to pending.scheduledAt), f.scheduler.deliveries)
        f.clock.advance(75 * 60_000)
        assertTrue(recreated.fire(pending.id))
        assertEquals("RINGING", f.store.occurrence(pending.id)!!.state)
        val tomorrow = f.pending(alarm.id)
        assertNotEquals(pending.id, tomorrow.id)
        assertEquals(pending.scheduledAt + 24 * 60 * 60_000, tomorrow.scheduledAt)
        assertFalse(recreated.dismiss(pending.id, ""))
    }

    @Test fun latenessHasSameMeaningAtDeliveryAndRecovery() = runTest {
        for (recoverFirst in listOf(false, true)) {
            val f = AlarmFixture()
            val alarm = Alarm(hour = 8, minute = 15, days = 127)
            f.engine.save(alarm)
            val pending = f.pending(alarm.id)
            f.clock.advance(86 * 60_000)
            if (recoverFirst) f.engine.recover()
            assertFalse(f.engine.fire(pending.id))
            assertEquals("MISSED", f.store.occurrence(pending.id)!!.state)
            assertFalse(pending.id in f.scheduler.deliveries)
            assertEquals(1, f.engine.sessions.value.count { it.state == "MISSED" })
            val tomorrow = f.pending(alarm.id)
            assertTrue(tomorrow.scheduledAt > f.clock.wall)
            f.engine.recover()
            assertEquals(tomorrow.id, f.pending(alarm.id).id)
        }
    }

    @Test fun expiredOneShotCannotBeResurrectedByRepeatedRecovery() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15, days = 0)
        f.engine.save(alarm)
        val pending = f.pending(alarm.id)
        f.clock.advance(90 * 60_000)
        repeat(3) { f.engine.recover() }
        assertFalse(f.store.alarm(alarm.id)!!.enabled)
        assertEquals("MISSED", f.store.occurrence(pending.id)!!.state)
        assertTrue(f.scheduler.deliveries.isEmpty())
    }

    @Test fun failedRegistrationIsVisibleAndRetryKeepsIdentity() = runTest {
        val f = AlarmFixture()
        val alarm = Alarm(hour = 8, minute = 15)
        f.scheduler.enabled = false
        f.engine.save(alarm)
        val failed = f.pending(alarm.id)
        assertNotNull(failed.scheduleError)
        assertTrue(f.scheduler.deliveries.isEmpty())
        f.scheduler.enabled = true
        f.engine.recover(RecoveryReason.EXACT_PERMISSION)
        assertEquals(failed.id, f.pending(alarm.id).id)
        assertNull(f.pending(alarm.id).scheduleError)
        assertEquals(setOf(failed.id), f.scheduler.deliveries.keys)
    }

    @Test fun timerUsesMonotonicTimeAndRebootReanchorsWithoutRevivingPausedCallback() = runTest {
        val f = AlarmFixture()
        f.engine.startTimer(60_000, math = true, vibrate = false)
        val original = f.engine.timers.value.single()
        f.clock.wall += 48 * 60 * 60_000
        assertFalse(f.engine.fire(original.occurrenceId))
        f.clock.advance(30_000)
        f.engine.pauseTimer(original.id)
        assertEquals(30_000, f.engine.timers.value.single().remaining)
        f.engine.recover(RecoveryReason.CLOCK_CHANGED)
        assertEquals("PAUSED", f.engine.timers.value.single().state)
        assertFalse(f.engine.fire(original.occurrenceId))
        f.engine.resumeTimer(original.id)
        val resumed = f.engine.timers.value.single()
        assertNotEquals(original.occurrenceId, resumed.occurrenceId)
        f.clock.wall += 15_000
        f.clock.boot++
        f.clock.elapsed = 100
        f.engine.recover(RecoveryReason.BOOT)
        val reanchored = f.engine.timers.value.single()
        assertEquals(15_100, reanchored.endElapsed)
        f.clock.advance(15_000)
        assertTrue(f.engine.fire(resumed.occurrenceId))
        val ringing = f.store.occurrence(resumed.occurrenceId)!!
        assertFalse(f.engine.dismiss(ringing.id, null))
        assertTrue(f.engine.dismiss(ringing.id, MathChallenge(ringing.left, ringing.right, ringing.subtract).answer.toString()))
        assertTrue(f.engine.timers.value.isEmpty())
        assertTrue(f.scheduler.deliveries.isEmpty())
    }
}

internal class AlarmFixture {
    val store = MemoryExecutionStore()
    val scheduler = MemoryAlarmScheduler()
    val clock = TestExecutionClock()
    val engine = AlarmEngine(store, ExecutionCore(clock, scheduler, Dispatchers.Unconfined))
    fun pending(id: String) = engine.sessions.value.single { it.alarmId == id && it.state == "PENDING" }
}
internal class TestExecutionClock : ExecutionClock {
    var wall = Instant.parse("2026-10-09T23:00:00Z").toEpochMilli()
    var elapsed = 100_000L
    var boot = 5
    override fun now() = ExecutionTime(wall, elapsed, boot, ZoneId.of("Asia/Shanghai"))
    fun advance(millis: Long) { wall += millis; elapsed += millis }
}
internal class MemoryAlarmScheduler : ExecutionScheduler {
    var enabled = true
    val deliveries = linkedMapOf<String, Long>()
    override fun exactAllowed() = enabled
    override fun schedule(wake: ScheduledWake): ScheduleResult {
        if (!enabled) return ScheduleResult("精确闹钟未授权")
        deliveries[wake.key.id] = wake.at
        return ScheduleResult()
    }
    override fun cancel(key: WakeKey) { deliveries.remove(key.id) }
}
internal class MemoryExecutionStore : AlarmExecutionStore {
    private val alarmMap = linkedMapOf<String, Alarm>()
    private val occurrenceMap = linkedMapOf<String, RingingSession>()
    private val timerMap = linkedMapOf<String, TimerSession>()
    override val sessions = MutableStateFlow<List<RingingSession>>(emptyList())
    override val timers = MutableStateFlow<List<TimerSession>>(emptyList())
    override suspend fun synchronize() = Unit
    override fun alarms() = alarmMap.values.toList()
    override suspend fun alarm(id: String) = alarmMap[id]
    override suspend fun saveAlarm(alarm: Alarm) { alarmMap[alarm.id] = alarm }
    override suspend fun deleteAlarm(id: String) { alarmMap.remove(id) }
    override fun occurrences() = occurrenceMap.values.toList()
    override fun occurrence(id: String) = occurrenceMap[id]
    override fun timer(id: String) = timerMap[id]
    override fun timerSnapshots() = timerMap.values.toList()
    override suspend fun write(session: RingingSession, timer: TimerSession?) {
        occurrenceMap[session.id] = session
        if (timer != null) timerMap[timer.id] = timer
        publish()
    }
    override fun writeTimer(timer: TimerSession) { timerMap[timer.id] = timer; publish() }
    private fun publish() {
        sessions.value = occurrenceMap.values.sortedBy { it.scheduledAt }
        timers.value = timerMap.values.filter { it.state in listOf("RUNNING", "PAUSED", "RINGING") }
    }
}
