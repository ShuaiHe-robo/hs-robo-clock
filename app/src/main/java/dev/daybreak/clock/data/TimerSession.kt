package dev.daybreak.clock.data

import dev.daybreak.clock.domain.TimerRules
import java.util.UUID

data class TimerSession(
    val id: String = UUID.randomUUID().toString(),
    val occurrenceId: String = UUID.randomUUID().toString(),
    val duration: Long,
    val remaining: Long = duration,
    val endElapsed: Long = 0,
    val endAt: Long = 0,
    val boot: Int = -1,
    val mathUnlockEnabled: Boolean = false,
    val vibrate: Boolean = true,
    val state: String = "RUNNING"
) {
    fun remainingAt(currentBoot: Int, elapsed: Long, now: Long): Long = when (state) {
        "RUNNING" -> TimerRules.remaining(duration, endElapsed, endAt, boot, currentBoot, elapsed, now)
        "PAUSED" -> remaining
        else -> 0
    }
}

val RingingSession.isTimer get() = alarmId.startsWith("timer:")
