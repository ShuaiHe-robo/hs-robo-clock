package dev.daybreak.clock.domain

import java.time.*
import kotlin.random.Random

/** Monday is bit 0. Empty alarm days means a single upcoming occurrence. */
object TimeRules {
    fun selected(mask: Int, day: DayOfWeek) = mask and (1 shl (day.value - 1)) != 0

    fun nextAlarm(hour: Int, minute: Int, days: Int, now: Instant, zone: ZoneId): Instant {
        require(hour in 0..23 && minute in 0..59 && days in 0..127)
        val date = now.atZone(zone).toLocalDate()
        return (0L..8L).asSequence().map { date.plusDays(it) }
            .filter { days == 0 || selected(days, it.dayOfWeek) }
            .flatMap { instants(it.atTime(hour, minute), zone).asSequence() }
            .first { it > now }
    }

    // Both occurrences of an overlapping local time are eligible. Gaps move forward.
    private fun instants(time: LocalDateTime, zone: ZoneId): List<Instant> {
        val offsets = zone.rules.getValidOffsets(time)
        return if (offsets.isEmpty()) listOf(time.atZone(zone).toInstant())
        else offsets.map { time.toInstant(it) }.sorted()
    }

    data class Window(val start: Instant, val end: Instant)
    /** Upcoming start only; an in-progress or released window must not show its end as a new start. */
    fun nextFocusStart(startMinute: Int, days: Int, now: Instant, zone: ZoneId): Instant {
        require(startMinute in 0..1439 && days in 1..127)
        val today = now.atZone(zone).toLocalDate()
        return (0L..8L).asSequence().map { today.plusDays(it) }
            .filter { selected(days, it.dayOfWeek) }
            .map { it.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(zone).toInstant() }
            .first { it > now }
    }

    fun focusWindow(startMinute: Int, endMinute: Int, days: Int, now: Instant, zone: ZoneId): Window? {
        require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute)
        val today = now.atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today).mapNotNull { day ->
            if (!selected(days, day.dayOfWeek)) return@mapNotNull null
            val start = day.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(zone).toInstant()
            val endDay = if (endMinute < startMinute) day.plusDays(1) else day
            val end = endDay.atStartOfDay().plusMinutes(endMinute.toLong()).atZone(zone).toInstant()
            Window(start, end).takeIf { now >= start && now < end }
        }.firstOrNull()
    }

    fun nextFocusBoundary(startMinute: Int, endMinute: Int, days: Int, now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..8L).flatMap { offset ->
            val day = today.plusDays(offset)
            if (!selected(days, day.dayOfWeek)) emptyList()
            else listOf(
                day.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(zone).toInstant(),
                (if (endMinute < startMinute) day.plusDays(1) else day).atStartOfDay()
                    .plusMinutes(endMinute.toLong()).atZone(zone).toInstant()
            )
        }.filter { it > now }.min()
    }
}

data class MathChallenge(val left: Int, val right: Int, val subtract: Boolean) {
    val answer: Int get() = if (subtract) left - right else left + right
    val question: String get() = "$left ${if (subtract) "−" else "+"} $right"
    fun accepts(input: String) = input.trim().toIntOrNull() == answer
    companion object {
        fun generate(random: Random = Random.Default): MathChallenge {
            val a = random.nextInt(10, 100)
            val b = random.nextInt(10, 100)
            val subtract = random.nextBoolean()
            return if (subtract) MathChallenge(maxOf(a, b), minOf(a, b), true)
            else MathChallenge(a, b, false)
        }
    }
}

object EmergencyWait {
    const val DURATION_MS = 60_000L
    fun remaining(startElapsed: Long, boot: Int, currentElapsed: Long, currentBoot: Int): Long {
        if (boot != currentBoot || currentElapsed < startElapsed) return DURATION_MS
        return (DURATION_MS - (currentElapsed - startElapsed)).coerceAtLeast(0L)
    }
}
