package dev.daybreak.clock.domain

import org.junit.Assert.*
import org.junit.Test

class TimerRulesTest {
    @Test fun clockChangesDoNotAffectRunningCountdown() {
        assertEquals(3000L, TimerRules.remaining(5000, 8000, 100_000, 7, 7, 5000, 1_000_000))
        assertEquals(3000L, TimerRules.remaining(5000, 8000, 100_000, 7, 7, 5000, 1))
    }
    @Test fun rebootUsesWallDeadlineWithoutExtendingPastOriginalDuration() {
        assertEquals(2000L, TimerRules.remaining(5000, 8000, 100_000, 7, 8, 100, 98_000))
        assertEquals(5000L, TimerRules.remaining(5000, 8000, 100_000, 7, 8, 100, 1))
        assertEquals(0L, TimerRules.remaining(5000, 8000, 100_000, 7, 8, 100, 101_000))
    }
    @Test fun digitsRoundUpSoASecondIsNotShownAsFinishedEarly() {
        assertEquals("00:00:01", TimerRules.digits(1))
        assertEquals("01:00:00", TimerRules.digits(3_599_001))
        assertEquals("24:00:00", TimerRules.digits(TimerRules.MAX_DURATION))
        assertEquals("00:00:00", TimerRules.digits(-1))
    }
}
