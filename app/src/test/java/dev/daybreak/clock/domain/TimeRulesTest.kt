package dev.daybreak.clock.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.random.Random

class TimeRulesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    @Test fun singleAlarmMovesToTomorrowAfterTodaysTime() {
        val now = Instant.parse("2026-10-08T00:01:00Z")
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), TimeRules.nextAlarm(8, 0, 0, now, shanghai))
    }
    @Test fun weekdayAlarmSkipsWeekend() {
        assertEquals(Instant.parse("2026-10-11T23:00:00Z"), TimeRules.nextAlarm(7, 0, 31,
            Instant.parse("2026-10-09T02:00:00Z"), shanghai))
    }
    @Test fun springGapUsesFirstValidShiftedLocalTime() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), TimeRules.nextAlarm(2, 30, 127,
            Instant.parse("2026-03-08T05:00:00Z"), zone))
    }
    @Test fun autumnOverlapCanUseLaterOffset() {
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), TimeRules.nextAlarm(1, 30, 127,
            Instant.parse("2026-11-01T05:45:00Z"), ZoneId.of("America/New_York")))
    }
    @Test fun midnightWindowUsesStartDaysWeekday() {
        val friday = 1 shl 4
        val window = TimeRules.focusWindow(23 * 60, 60, friday, Instant.parse("2026-10-09T16:30:00Z"), shanghai)!!
        assertEquals(Instant.parse("2026-10-09T15:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-10-09T17:00:00Z"), window.end)
        assertNull(TimeRules.focusWindow(23 * 60, 60, friday, window.end, shanghai))
    }
    @Test fun boundaryIncludesPreviousDaysMidnightEnd() {
        assertEquals(Instant.parse("2026-10-09T17:00:00Z"), TimeRules.nextFocusBoundary(23 * 60, 60, 1 shl 4,
            Instant.parse("2026-10-09T16:30:00Z"), shanghai))
    }
    @Test fun upcomingFocusStartDoesNotUseCurrentWindowsEnd() {
        val now = Instant.parse("2026-10-08T02:00:00Z") // Thursday 10:00, within 09:00–11:00.
        assertEquals(Instant.parse("2026-10-08T03:00:00Z"), TimeRules.nextFocusBoundary(540, 660, 31, now, shanghai))
        assertEquals(Instant.parse("2026-10-09T01:00:00Z"), TimeRules.nextFocusStart(540, 31, now, shanghai))
    }
    @Test fun upcomingFocusStartSkipsWeekend() {
        assertEquals(Instant.parse("2026-10-12T01:00:00Z"), TimeRules.nextFocusStart(540, 31,
            Instant.parse("2026-10-09T02:00:00Z"), shanghai))
    }
    @Test fun upcomingFocusStartMatchesWindowsOffsetDuringAutumnOverlap() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-11-01T05:45:00Z")
        // focusWindow uses the first offset. The second 01:30 is not a new window.
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), TimeRules.nextFocusStart(90, 127, now, zone))
    }
    @Test(expected = IllegalArgumentException::class) fun equalTimesAreRejected() {
        TimeRules.focusWindow(60, 60, 127, Instant.now(), shanghai)
    }
    @Test fun questionsHaveTwoDigitOperandsAndNonNegativeIntegerResults() {
        val random = Random(7)
        repeat(1000) {
            val q = MathChallenge.generate(random)
            assertTrue(q.left in 10..99 && q.right in 10..99 && q.answer >= 0)
            assertTrue(q.accepts(q.answer.toString()))
            assertFalse(q.accepts((q.answer + 1).toString()))
            assertFalse(q.accepts("oops"))
            assertFalse(q.accepts("${q.answer}.0"))
        }
    }
    @Test fun emergencyWaitUsesElapsedTimeAndRestartsAfterBoot() {
        assertEquals(60_000L, EmergencyWait.remaining(1000, 2, 1000, 2))
        assertEquals(1L, EmergencyWait.remaining(1000, 2, 60_999, 2))
        assertEquals(0L, EmergencyWait.remaining(1000, 2, 61_000, 2))
        assertEquals(60_000L, EmergencyWait.remaining(1000, 2, 90_000, 3))
        assertEquals(60_000L, EmergencyWait.remaining(1000, 2, 900, 2))
    }
}
