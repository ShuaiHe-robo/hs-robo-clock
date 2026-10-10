package dev.daybreak.clock.domain.focus

import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.data.FocusSession

/** Reads and mutations share one durable transaction; observers publish only after commit. */
interface FocusExecutionStore {
    suspend fun <T> transaction(command: suspend FocusRecords.() -> T): T
}
interface FocusRecords {
    suspend fun rules(): List<FocusRule>
    suspend fun protectedSessions(): List<FocusSession>
    suspend fun windowSessions(id: String): List<FocusSession>
    suspend fun releaseSessions(token: String): List<FocusSession>
    suspend fun saveRule(rule: FocusRule)
    suspend fun deleteRule(id: String)
    suspend fun saveSession(session: FocusSession)
}
fun interface FocusTargetPolicy { fun isProtected(packageName: String): Boolean }
enum class FocusState {
    ACTIVE, RELEASE_PENDING, EXPIRED, EMERGENCY_RELEASED;
    fun permits(next: FocusState) = this == next || when (this) {
        ACTIVE -> next == RELEASE_PENDING || next == EXPIRED
        RELEASE_PENDING -> next in setOf(ACTIVE, EXPIRED, EMERGENCY_RELEASED)
        else -> false
    }
}
fun FocusSession.lifecycle() = FocusState.entries.first { it.name == state }

data class FocusPresentation(val packageName: String, val endAt: Long, val seconds: Long,
    val releaseToken: String?, val waitRemaining: Long)
data class FocusBoundary(val at: Long? = null, val exact: Boolean = true, val error: String? = null)
