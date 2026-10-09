package dev.daybreak.clock

import android.app.Application
import android.content.Context
import android.os.UserManager
import dev.daybreak.clock.data.*
import dev.daybreak.clock.platform.alarm.AlarmEngine
import dev.daybreak.clock.platform.focus.FocusEngine
import kotlinx.coroutines.*

class ClockApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val bootStore by lazy { BootStore(this) }
    val database by lazy { ClockDatabase.create(this) }
    val preferences by lazy { Preferences(this) }
    val alarms by lazy { AlarmEngine(this, bootStore) }
    val focus by lazy { FocusEngine(this) }
    fun unlocked() = getSystemService(UserManager::class.java).isUserUnlocked
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try { alarms.recover(); if (unlocked()) focus.refresh() }
            catch (e: Exception) { android.util.Log.e("Daybreak", "Recovery failed", e) }
        }
    }
}
val Context.clock: ClockApplication get() = applicationContext as ClockApplication
