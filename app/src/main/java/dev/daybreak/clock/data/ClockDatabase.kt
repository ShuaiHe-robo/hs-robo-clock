package dev.daybreak.clock.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Int = 31,
    val enabled: Boolean = true,
    val label: String = "",
    val ringtoneUri: String? = null,
    val ringtoneSource: String = "DEFAULT",
    val ringtoneDisplayName: String = "默认闹钟铃声",
    val vibrate: Boolean = true,
    val mathUnlockEnabled: Boolean = false,
    val difficulty: Int = 1,
    val revision: Long = 1
)

@Entity(tableName = "ringing_sessions")
data class RingingSession(
    @PrimaryKey val id: String,
    val alarmId: String,
    val revision: Long,
    val scheduledAt: Long,
    val hour: Int,
    val minute: Int,
    val days: Int,
    val label: String,
    val ringtoneUri: String?,
    val ringtoneSource: String,
    val ringtoneDisplayName: String,
    val vibrate: Boolean,
    val mathUnlockEnabled: Boolean,
    val left: Int,
    val right: Int,
    val subtract: Boolean,
    val state: String = "PENDING",
    val scheduleError: String? = null,
    val fallbackUsed: Boolean = false
)

@Entity(tableName = "focus_rules")
data class FocusRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val label: String = "专注时间",
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 11 * 60,
    val days: Int = 31,
    val packages: String = "",
    val enabled: Boolean = true
) {
    fun targets() = packages.split('\n').filter { it.isNotBlank() }.toSet()
}

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey val id: String,
    val ruleId: String,
    val label: String,
    val startAt: Long,
    val endAt: Long,
    val packages: String,
    val state: String = "ACTIVE",
    val releaseToken: String? = null,
    val waitStartedAt: Long = 0,
    val waitElapsed: Long = 0,
    val waitBoot: Int = -1,
    val releasedAt: Long? = null
) {
    fun targets() = packages.split('\n').filter { it.isNotBlank() }.toSet()
    fun protected() = state == "ACTIVE" || state == "RELEASE_PENDING"
}

@Dao
interface ClockDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute") fun observeAlarms(): Flow<List<Alarm>>
    @Query("SELECT * FROM alarms") suspend fun alarms(): List<Alarm>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun alarm(id: String): Alarm?
    @Upsert suspend fun saveAlarm(alarm: Alarm)
    @Query("DELETE FROM alarms WHERE id = :id") suspend fun deleteAlarm(id: String)
    @Upsert suspend fun saveRinging(session: RingingSession)
    @Query("SELECT * FROM ringing_sessions ORDER BY scheduledAt DESC LIMIT 100") fun observeRinging(): Flow<List<RingingSession>>
    @Query("SELECT * FROM focus_rules ORDER BY startMinute") fun observeFocusRules(): Flow<List<FocusRule>>
    @Query("SELECT * FROM focus_rules") suspend fun focusRules(): List<FocusRule>
    @Upsert suspend fun saveFocusRule(rule: FocusRule)
    @Query("DELETE FROM focus_rules WHERE id = :id") suspend fun deleteFocusRule(id: String)
    @Query("SELECT * FROM focus_sessions ORDER BY startAt DESC LIMIT 200") fun observeFocusSessions(): Flow<List<FocusSession>>
    @Query("SELECT * FROM focus_sessions WHERE state IN ('ACTIVE', 'RELEASE_PENDING')") suspend fun protectedFocusSessions(): List<FocusSession>
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun focusSession(id: String): FocusSession?
    @Query("SELECT * FROM focus_sessions WHERE id = :windowId OR id LIKE :windowId || ':%'")
    suspend fun focusWindowSessions(windowId: String): List<FocusSession>
    @Query("SELECT * FROM focus_sessions WHERE releaseToken = :token") suspend fun releaseSessions(token: String): List<FocusSession>
    @Upsert suspend fun saveFocusSession(session: FocusSession)
}

@Database(entities = [Alarm::class, RingingSession::class, FocusRule::class, FocusSession::class], version = 1, exportSchema = true)
abstract class ClockDatabase : RoomDatabase() {
    abstract fun dao(): ClockDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, ClockDatabase::class.java, "clock.db").build()
    }
}
