package dev.daybreak.clock.domain

/** Wall time is only a recovery fallback after a reboot; clock edits cannot change a live timer. */
object TimerRules {
    const val MAX_DURATION = 24 * 60 * 60 * 1000L
    fun remaining(duration: Long, endElapsed: Long, endAt: Long, savedBoot: Int,
        currentBoot: Int, elapsed: Long, now: Long): Long =
        (if (savedBoot == currentBoot) endElapsed - elapsed else endAt - now).coerceIn(0, duration)

    fun digits(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0) + 999) / 1000
        return java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d:%02d",
            seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
