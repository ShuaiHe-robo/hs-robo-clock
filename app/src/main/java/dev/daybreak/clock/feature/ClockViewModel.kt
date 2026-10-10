package dev.daybreak.clock.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.daybreak.clock.ClockApplication
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.execution.RecoveryReason
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ClockState(val alarms: List<Alarm> = emptyList(), val focusRules: List<FocusRule> = emptyList(), val history: List<FocusSession> = emptyList(), val loading: Boolean = true)
class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClockApplication
    val state = combine(app.database.dao().observeAlarms(), app.database.dao().observeFocusRules(), app.database.dao().observeFocusSessions()) {
        alarms, rules, sessions -> ClockState(alarms, rules, sessions, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClockState())
    val ringing = app.alarms.sessions
    val timers = app.alarms.timers
    val alarmReliability = app.alarmReliability.state
    val focus = app.focus.sessions
    val focusBoundary = app.focus.boundary
    val dark = app.preferences.dark.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    private val errors = MutableStateFlow<String?>(null)
    val error = errors.asStateFlow()
    val busy = MutableStateFlow(false)
    fun clearError() { errors.value = null }
    fun report(message: String) { errors.value = message }
    private fun operate(done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { withContext(Dispatchers.IO) { block() }; done() }
            catch (e: Exception) { errors.value = e.message ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }
    fun saveAlarm(alarm: Alarm, done: () -> Unit = {}) = operate(done) { app.alarms.save(alarm) }
    fun deleteAlarm(id: String, done: () -> Unit = {}) = operate(done) { app.alarms.delete(id) }
    fun startTimer(duration: Long, math: Boolean, vibrate: Boolean) = operate { app.alarms.startTimer(duration, math, vibrate) }
    fun pauseTimer(id: String) = operate { app.alarms.pauseTimer(id) }
    fun resumeTimer(id: String) = operate { app.alarms.resumeTimer(id) }
    fun cancelTimer(id: String) = operate { app.alarms.cancelTimer(id) }
    fun saveFocus(rule: FocusRule, done: () -> Unit = {}) = operate(done) { app.focus.save(rule) }
    fun deleteFocus(id: String, done: () -> Unit = {}) = operate(done) { app.focus.delete(id) }
    fun rebuild() = operate { app.recoverSchedules(RecoveryReason.APP_RESUME)?.let { throw IllegalStateException(it) } }
    fun acknowledgeAlarmWarnings(stoppedAt: Long, missedAt: Long) = operate { app.alarmReliability.acknowledge(stoppedAt, missedAt) }
    fun theme(dark: Boolean) = operate { app.preferences.setDark(dark) }
    fun beginRelease() = operate { app.focus.beginRelease() }
    fun cancelRelease(token: String) = operate { app.focus.cancelRelease(token) }
    fun confirmRelease(token: String) = operate { if (!app.focus.confirmRelease(token)) errors.value = "等待尚未完成，或窗口已结束" }
}
