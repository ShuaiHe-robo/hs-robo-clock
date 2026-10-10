package dev.daybreak.clock.domain.focus

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FocusControllerTest {
    @Test fun existingForegroundAppIsCoveredAndNaturalEndRemovesOutput() = runTest {
        val f = FocusFixture()
        f.engine.save(f.rule(end = 9 * 60 + 1))
        var current: FocusPresentation? = null
        var foreground = "blocked.a"
        val checks = mutableListOf<FocusCheck>()
        val controller = FocusController(f.engine, this, FocusForeground { foreground }, FocusOutput { current = it }, checks::add)
        try {
            controller.checkOnce()
            assertEquals("blocked.a", current!!.packageName)
            foreground = "system.home"; controller.windowChanged()
            assertNull(current)
            foreground = "blocked.a"; controller.windowChanged()
            assertNotNull(current)
            f.clock.advance(60_000); controller.checkOnce()
            assertNull(current); assertEquals(FocusCheck.RUNNING, checks.last())
        } finally { controller.close() }
    }
    @Test fun outputAndStorageFailuresAreRetryableWithoutReconnecting() = runTest {
        val f = FocusFixture()
        f.engine.save(f.rule())
        var failOutput = true
        var current: FocusPresentation? = null
        val checks = mutableListOf<FocusCheck>()
        val controller = FocusController(f.engine, this, FocusForeground { "blocked.a" }, FocusOutput {
            if (failOutput) error("Window not available")
            current = it
        }, checks::add)
        try {
            controller.checkOnce(); assertEquals(FocusCheck.OUTPUT_UNAVAILABLE, checks.last())
            failOutput = false; controller.checkOnce()
            assertNotNull(current); assertEquals(FocusCheck.RUNNING, checks.last())
            f.store.failCommit = true; controller.checkOnce()
            assertEquals(FocusCheck.CHECK_FAILED, checks.last())
            f.store.failCommit = false; controller.checkOnce()
            assertNotNull(current); assertEquals(FocusCheck.RUNNING, checks.last())
        } finally { failOutput = false; controller.close() }
        assertNull(current)
        assertEquals(setOf("blocked.a"), f.engine.blockedPackages())
    }
}
