package dev.daybreak.clock.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTextTest {
    @Test fun futureEventsKeepOneMinuteDuringTheLastPartialMinute() {
        assertEquals("1 分钟", countdownText(1))
        assertEquals("1 分钟", countdownText(59_999))
        assertEquals("2 分钟", countdownText(60_001))
    }
    @Test fun wholeHourAndMinuteRemainVisibleTogether() {
        assertEquals("12 小时 1 分钟", countdownText((12 * 60 + 1) * 60_000L))
        assertEquals("1 小时 0 分钟", countdownText(60 * 60_000L))
    }
    @Test fun dueAndPastEventsNeverShowNegativeTime() {
        assertEquals("0 分钟", countdownText(0))
        assertEquals("0 分钟", countdownText(-60_000))
    }
}
