package dev.daybreak.clock.domain.focus

import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.execution.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FocusEngineTest {
    @Test fun releaseScopeSurvivesDeletionAndExcludesLaterWindows() = runTest {
        val f = FocusFixture()
        val first = f.rule("first", "blocked.a")
        val second = f.rule("second", "blocked.b")
        f.engine.save(first); f.engine.save(second)
        val token = f.engine.beginRelease()!!
        f.engine.delete(first.id); f.engine.delete(second.id)
        f.engine.save(f.rule("later", "blocked.c"))
        assertEquals(setOf("blocked.a", "blocked.b", "blocked.c"), f.engine.blockedPackages())
        assertFalse(f.engine.confirmRelease(token))
        f.engine.cancelRelease(token)
        assertEquals(setOf("ACTIVE"), f.engine.sessions.value.map { it.state }.toSet())
        val retry = f.engine.beginRelease()!!
        f.clock.advance(60_001)
        f.engine.save(f.rule("newest", "blocked.d"))
        assertTrue(f.engine.confirmRelease(retry))
        assertEquals(setOf("blocked.d"), f.engine.blockedPackages())
        repeat(3) { f.engine.refresh() }
        assertEquals(setOf("blocked.d"), f.engine.blockedPackages())
        assertTrue(f.store.sessions.values.filter { it.releaseToken == retry }.all { it.state == "EMERGENCY_RELEASED" })
    }
    @Test fun extendingPlanPreservesFrozenTargetsAndCreatesOnlyOneLaterInterval() = runTest {
        val f = FocusFixture()
        val rule = f.rule(end = 9 * 60 + 5)
        f.engine.save(rule)
        val initial = f.engine.sessions.value.single()
        f.engine.save(rule.copy(endMinute = 9 * 60 + 10, packages = "blocked.b"))
        assertEquals(listOf(initial), f.engine.sessions.value)
        assertEquals(initial.endAt, f.engine.boundary.value.at)
        f.clock.advance(5 * 60_000)
        repeat(4) { f.engine.refresh() }
        val extension = f.engine.sessions.value.single()
        assertEquals("blocked.b", extension.packages)
        assertNull(extension.releaseToken)
        assertEquals(initial.endAt, extension.startAt)
        assertEquals("EXPIRED", f.store.sessions[initial.id]!!.state)
    }
    @Test fun releasedExtensionIsNotRevivedByItsExpiredParent() = runTest {
        val f = FocusFixture()
        val rule = f.rule(end = 9 * 60 + 2)
        f.engine.save(rule)
        val token = f.engine.beginRelease()!!
        f.clock.advance(60_001)
        assertTrue(f.engine.confirmRelease(token))
        f.engine.save(rule.copy(endMinute = 9 * 60 + 10))
        assertTrue(f.engine.sessions.value.isEmpty())
        f.clock.advance(60_000)
        f.engine.refresh()
        val extension = f.engine.sessions.value.single()
        val extensionToken = f.engine.beginRelease()!!
        f.clock.advance(60_001)
        assertTrue(f.engine.confirmRelease(extensionToken))
        val recreated = f.recreate()
        repeat(3) { recreated.recover(RecoveryReason.PROCESS_START) }
        assertTrue(recreated.blockedPackages().isEmpty())
        assertEquals("EMERGENCY_RELEASED", f.store.sessions[extension.id]!!.state)
    }
    @Test fun rebootRestartsReleaseWaitAndWallClockCannotShortenIt() = runTest {
        val f = FocusFixture()
        f.engine.save(f.rule())
        val token = f.engine.beginRelease()!!
        f.clock.advance(30_000)
        f.clock.wall += 30_000
        assertEquals(30_000L, f.engine.remaining(token))
        f.clock.boot++; f.clock.elapsed = 100
        val recreated = f.recreate()
        recreated.recover(RecoveryReason.BOOT)
        assertEquals(60_000L, recreated.remaining(token))
        assertFalse(recreated.confirmRelease(token))
        f.clock.advance(60_000)
        assertTrue(recreated.confirmRelease(token))
    }
    @Test fun recoveryRearmsAnUnchangedDeadlineAndFailedScheduleRemainsRetryable() = runTest {
        val f = FocusFixture()
        f.engine.save(f.rule())
        val at = f.engine.boundary.value.at
        f.scheduler.wakes.clear() // Platform registration lost, while process cache remains.
        f.engine.recover(RecoveryReason.APP_RESUME)
        assertEquals(at, f.scheduler.wakes[WakeKey.focus])
        f.scheduler.fail = true
        f.engine.recover(RecoveryReason.EXACT_PERMISSION)
        assertNotNull(f.engine.boundary.value.error)
        assertEquals(setOf("blocked.a"), f.engine.blockedPackages())
        f.scheduler.fail = false
        f.engine.refresh()
        assertNull(f.engine.boundary.value.error)
        assertEquals(at, f.scheduler.wakes[WakeKey.focus])
    }
    @Test fun transactionFailurePublishesNoPartialPlanOrReleaseState() = runTest {
        val f = FocusFixture()
        f.store.failCommit = true
        assertFails { f.engine.save(f.rule()) }
        assertTrue(f.store.rules.isEmpty()); assertTrue(f.store.sessions.isEmpty())
        assertTrue(f.engine.sessions.value.isEmpty()); assertTrue(f.scheduler.wakes.isEmpty())
        f.store.failCommit = false
        f.engine.save(f.rule())
        val original = f.engine.sessions.value
        f.store.failCommit = true
        assertFails { f.engine.beginRelease() }
        assertEquals(original, f.engine.sessions.value)
        assertEquals(original, f.store.sessions.values.toList())
    }
    @Test fun protectedTargetsCannotEnterAPlanAndExpiredSnapshotsCannotReturn() = runTest {
        val f = FocusFixture()
        assertFails { f.engine.save(f.rule(packages = "system.settings")) }
        assertTrue(f.store.rules.isEmpty())
        f.engine.save(f.rule(end = 9 * 60 + 2))
        f.engine.delete("rule")
        f.clock.advance(120_000)
        f.engine.boundaryDelivered()
        assertTrue(f.engine.sessions.value.isEmpty()); assertNull(f.engine.boundary.value.at)
        f.clock.wall -= 60_000
        f.engine.refresh()
        assertTrue(f.engine.blockedPackages().isEmpty())
    }
    private suspend fun assertFails(block: suspend () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
}

internal class FocusFixture {
    val clock = FocusTestClock()
    val scheduler = FocusTestScheduler()
    val core = ExecutionCore(clock, scheduler, Dispatchers.Unconfined)
    val store = MemoryFocusStore()
    val policy = FocusTargetPolicy { it.startsWith("system.") }
    val engine = FocusEngine(store, core, policy)
    fun recreate() = FocusEngine(store, ExecutionCore(clock, scheduler, Dispatchers.Unconfined), policy)
    fun rule(id: String = "rule", packages: String = "blocked.a", end: Int = 10 * 60) =
        FocusRule(id, "专注测试", 8 * 60, end, 127, packages)
}
internal class FocusTestClock : ExecutionClock {
    var wall = Instant.parse("2026-10-10T01:00:00Z").toEpochMilli()
    var elapsed = 100_000L
    var boot = 1
    override fun now() = ExecutionTime(wall, elapsed, boot, ZoneId.of("Asia/Shanghai"))
    fun advance(ms: Long) { wall += ms; elapsed += ms }
}
internal class FocusTestScheduler : ExecutionScheduler {
    val wakes = linkedMapOf<WakeKey, Long>()
    var registrations = 0
    var fail = false
    override fun exactAllowed() = true
    override fun schedule(wake: ScheduledWake): ScheduleResult {
        registrations++
        if (fail) return ScheduleResult("测试排程失败")
        wakes[wake.key] = wake.at
        return ScheduleResult()
    }
    override fun cancel(key: WakeKey) { wakes.remove(key) }
}
internal class MemoryFocusStore : FocusExecutionStore, FocusRecords {
    var rules = linkedMapOf<String, FocusRule>()
    var sessions = linkedMapOf<String, FocusSession>()
    var failCommit = false
    override suspend fun <T> transaction(command: suspend FocusRecords.() -> T): T {
        val oldRules = LinkedHashMap(rules); val oldSessions = LinkedHashMap(sessions)
        try {
            val result = command(this)
            check(!failCommit) { "Simulated commit failure" }
            return result
        } catch (e: Throwable) { rules = oldRules; sessions = oldSessions; throw e }
    }
    override suspend fun rules() = rules.values.toList()
    override suspend fun protectedSessions() = sessions.values.filter { it.protected() }
    override suspend fun windowSessions(id: String) = sessions.values.filter { it.id == id || it.id.startsWith("$id:") }
    override suspend fun releaseSessions(token: String) = sessions.values.filter { it.releaseToken == token }
    override suspend fun saveRule(rule: FocusRule) { rules[rule.id] = rule }
    override suspend fun deleteRule(id: String) { rules.remove(id) }
    override suspend fun saveSession(session: FocusSession) { sessions[session.id] = session }
}
