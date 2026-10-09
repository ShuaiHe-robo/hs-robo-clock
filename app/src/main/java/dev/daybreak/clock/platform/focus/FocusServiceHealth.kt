package dev.daybreak.clock.platform.focus

import android.os.SystemClock

enum class FocusProtectionStatus {
    DISABLED, DISCONNECTED, CHECK_FAILED, OVERLAY_UNAVAILABLE, RUNNING
}

data class FocusServiceHealth(
    val connected: Boolean = false,
    val lastCheckElapsed: Long? = null,
    val problem: FocusProtectionStatus? = null
) {
    fun status(enabled: Boolean, nowElapsed: Long): FocusProtectionStatus = when {
        !enabled -> FocusProtectionStatus.DISABLED
        !connected -> FocusProtectionStatus.DISCONNECTED
        problem != null -> problem
        lastCheckElapsed == null || nowElapsed - lastCheckElapsed !in 0..5_000 -> FocusProtectionStatus.CHECK_FAILED
        else -> FocusProtectionStatus.RUNNING
    }
}

// Process-local state intentionally starts disconnected after process death. A stored
// checkbox or timestamp cannot prove that Android has rebound the service this time.
object FocusServiceRuntime {
    @Volatile var health = FocusServiceHealth()
        private set

    fun connected() { health = FocusServiceHealth(connected = true) }
    fun checked(problem: FocusProtectionStatus? = null) {
        health = FocusServiceHealth(connected = true, lastCheckElapsed = SystemClock.elapsedRealtime(), problem = problem)
    }
    fun disconnected() { health = FocusServiceHealth() }
}
