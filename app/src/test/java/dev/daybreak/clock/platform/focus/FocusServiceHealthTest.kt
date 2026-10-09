package dev.daybreak.clock.platform.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusServiceHealthTest {
    @Test fun configuredServiceNeedsLiveCheckAfterProcessRestart() {
        assertEquals(FocusProtectionStatus.DISCONNECTED, FocusServiceHealth().status(true, 10_000))
        assertEquals(FocusProtectionStatus.CHECK_FAILED,
            FocusServiceHealth(connected = true).status(true, 10_000))
        assertEquals(FocusProtectionStatus.RUNNING,
            FocusServiceHealth(connected = true, lastCheckElapsed = 10_000).status(true, 11_000))
    }

    @Test fun stoppedChecksAndOldBootTimestampsCannotReportProtection() {
        val health = FocusServiceHealth(connected = true, lastCheckElapsed = 10_000)
        assertEquals(FocusProtectionStatus.CHECK_FAILED, health.status(true, 15_001))
        assertEquals(FocusProtectionStatus.CHECK_FAILED, health.status(true, 9_000))
        assertEquals(FocusProtectionStatus.DISABLED, health.status(false, 11_000))
    }

    @Test fun freshHeartbeatDoesNotHideFailedOverlayOrRefresh() {
        listOf(FocusProtectionStatus.OVERLAY_UNAVAILABLE, FocusProtectionStatus.CHECK_FAILED).forEach { problem ->
            assertEquals(problem, FocusServiceHealth(true, 10_000, problem).status(true, 11_000))
        }
    }
}
