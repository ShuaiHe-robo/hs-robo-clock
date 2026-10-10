package dev.daybreak.clock

import android.app.Application
import android.content.Context
import android.os.UserManager
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.alarm.AlarmEngine
import dev.daybreak.clock.domain.execution.RecoveryReason
import dev.daybreak.clock.domain.execution.ExecutionCore
import dev.daybreak.clock.domain.focus.FocusEngine
import dev.daybreak.clock.domain.focus.FocusTargetPolicy
import dev.daybreak.clock.platform.alarm.AndroidAlarmExecutionStore
import dev.daybreak.clock.platform.execution.AndroidExecutionScheduler
import dev.daybreak.clock.platform.execution.AndroidExecutionClock
import dev.daybreak.clock.platform.alarm.AlarmReliabilityStore
import dev.daybreak.clock.platform.focus.AndroidFocusExecutionStore
import dev.daybreak.clock.platform.focus.ProtectedApps
import kotlinx.coroutines.*

class ClockApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val bootStore by lazy { BootStore(this) }
    val database by lazy { ClockDatabase.create(this) }
    val preferences by lazy { Preferences(this) }
    val execution by lazy { ExecutionCore(AndroidExecutionClock(this), AndroidExecutionScheduler(this)) }
    val alarms by lazy { AlarmEngine(AndroidAlarmExecutionStore(this, bootStore), execution, alarmReliability) }
    val alarmReliability by lazy { AlarmReliabilityStore(this) }
    val focus by lazy {
        val safe = ProtectedApps(this)
        FocusEngine(AndroidFocusExecutionStore(database), execution, FocusTargetPolicy(safe::isProtected))
    }
    fun unlocked() = getSystemService(UserManager::class.java).isUserUnlocked
    /** One recovery entry point for process start, platform broadcasts and the foreground UI. */
    suspend fun recoverSchedules(reason: RecoveryReason): String? {
        val failures = mutableListOf<String>()
        try { alarms.recover(reason) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            failures += "闹钟计划恢复失败，请重试"
            alarmReliability.event("RECOVERY_FAILED", detail = e.toString())
            android.util.Log.e("Daybreak", "Alarm recovery failed", e)
        }
        if (unlocked()) try {
            focus.recover(reason)
            focus.boundary.value.error?.let { failures += "专注计划登记失败，请重试" }
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failures += "专注计划恢复失败，请重试"; android.util.Log.e("Daybreak", "Focus recovery failed", e) }
        return failures.takeIf { it.isNotEmpty() }?.joinToString("；")
    }
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            alarmReliability.captureLastExits(this@ClockApplication)
            recoverSchedules(RecoveryReason.PROCESS_START)
        }
    }
}
val Context.clock: ClockApplication get() = applicationContext as ClockApplication
