package dev.daybreak.clock.platform.focus

import androidx.room.withTransaction
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.focus.FocusExecutionStore
import dev.daybreak.clock.domain.focus.FocusRecords

class AndroidFocusExecutionStore(private val database: ClockDatabase) : FocusExecutionStore {
    private val records = object : FocusRecords {
        private val dao get() = database.dao()
        override suspend fun rules() = dao.focusRules()
        override suspend fun protectedSessions() = dao.protectedFocusSessions()
        override suspend fun windowSessions(id: String) = dao.focusWindowSessions(id)
        override suspend fun releaseSessions(token: String) = dao.releaseSessions(token)
        override suspend fun saveRule(rule: FocusRule) = dao.saveFocusRule(rule)
        override suspend fun deleteRule(id: String) = dao.deleteFocusRule(id)
        override suspend fun saveSession(session: FocusSession) = dao.saveFocusSession(session)
    }
    override suspend fun <T> transaction(command: suspend FocusRecords.() -> T): T =
        database.withTransaction { command(records) }
}
