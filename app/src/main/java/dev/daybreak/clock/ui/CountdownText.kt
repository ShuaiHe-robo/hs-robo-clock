package dev.daybreak.clock.ui

/** Round upward so a future event never appears already due during its last minute. */
fun countdownText(remainingMillis: Long): String {
    val minutes = (remainingMillis.coerceAtLeast(0) + 59_999) / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
        minutes > 0 -> "$minutes 分钟"
        else -> "0 分钟"
    }
}
