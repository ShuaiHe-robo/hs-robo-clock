package dev.daybreak.clock.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daybreak.clock.data.TimerSession
import dev.daybreak.clock.domain.TimerRules
import dev.daybreak.clock.feature.ClockViewModel
import dev.daybreak.clock.platform.Capabilities
import dev.daybreak.clock.platform.alarm.AlarmActivity
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimerScreen(vm: ClockViewModel, capabilities: Capabilities, busy: Boolean,
    onSettings: () -> Unit, bottomClearance: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timers by vm.timers.collectAsStateWithLifecycle()
    val sessions by vm.ringing.collectAsStateWithLifecycle()
    val timer = timers.firstOrNull()
    var hour by rememberSaveable { mutableStateOf("00") }
    var minute by rememberSaveable { mutableStateOf("05") }
    var second by rememberSaveable { mutableStateOf("00") }
    var math by rememberSaveable { mutableStateOf(false) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(timer?.id, timer?.state) {
        while (true) { now = System.currentTimeMillis(); elapsed = SystemClock.elapsedRealtime(); delay(200) }
    }
    val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    val left = timer?.remainingAt(boot, elapsed, now) ?: 0L
    val duration = ((hour.toLongOrNull() ?: 0) * 3600 + (minute.toLongOrNull() ?: 0) * 60 + (second.toLongOrNull() ?: 0)) * 1000
    val valid = (hour.toIntOrNull() ?: 0) in 0..24 && (minute.toIntOrNull() ?: 0) in 0..59 &&
        (second.toIntOrNull() ?: 0) in 0..59 && duration in 1000..TimerRules.MAX_DURATION
    val error = timer?.let { t -> sessions.firstOrNull { it.id == t.occurrenceId }?.scheduleError }
    BoxWithConstraints(modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = bottomClearance)) {
        val fontScale = LocalDensity.current.fontScale
        val wide = maxWidth >= 560.dp && maxHeight < 400.dp
        val compact = wide || maxHeight < 520.dp
        val showHeading = timer != null || (!wide && maxHeight >= 400.dp) ||
            !capabilities.exact || !capabilities.notifications || !capabilities.fullScreen || !capabilities.alarmOverlay
        val minHeight = (if (wide) (if (timer == null && showHeading) 260f else 180f)
            else if (timer == null) (if (!showHeading) 360f else if (compact) 400f else 460f) else 330f) * maxOf(1f, fontScale * .85f)
        val scroll = rememberScrollState()
        // Keep the dashboard fixed in ordinary windows; retain access with an open keyboard or extreme text sizes.
        val bodyModifier = Modifier.fillMaxWidth().then(if (maxHeight.value < minHeight) Modifier.verticalScroll(scroll) else Modifier)
        val heading: @Composable () -> Unit = {
            TimerHeading(timer, left, now, compact, capabilities, onSettings)
        }
        val timeCard: @Composable () -> Unit = {
            TimerTimeCard(hour, { hour = it }, minute, { minute = it }, second, { second = it }, duration, valid,
                compact || maxWidth < 340.dp || fontScale > 1.3f,
                preset = { hour = "00"; minute = it.toString().padStart(2, '0'); second = "00" })
        }
        val controls: @Composable () -> Unit = {
            if (timer == null) {
                TimerReminderCard(math, { math = it }, vibrate, { vibrate = it }, compact)
                Button(onClick = { vm.startTimer(duration, math, vibrate) }, enabled = valid && !busy && capabilities.exact,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("开始计时")
                }
            } else {
                TimerActiveCard(timer, left, error, busy, vm, cancel = { confirmCancel = true },
                    dismiss = { context.startActivity(Intent(context, AlarmActivity::class.java)) })
            }
        }
        if (wide) {
            Row(bodyModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showHeading) heading()
                    if (timer == null) timeCard()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        } else {
            Column(bodyModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showHeading) heading()
                if (timer == null) timeCard()
                controls()
            }
        }
    }
    if (confirmCancel && timer != null) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("取消本次计时？") }, text = { Text("剩余时间将清除，到时不再响铃。") },
        confirmButton = { TextButton(onClick = { vm.cancelTimer(timer.id); confirmCancel = false }) { Text("取消计时") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留计时") } })
}

@Composable
private fun TimerHeading(timer: TimerSession?, left: Long, now: Long, compact: Boolean,
    capabilities: Capabilities, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(when (timer?.state) {
            "RUNNING" -> if (left == 0L) "正在唤起响铃" else "距离计时结束还有"
            "PAUSED" -> "计时已暂停"
            "RINGING" -> "时间到了"
            else -> "计时器"
        }, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        // Idle time is already editable in the three fields; do not repeat a second oversized clock above them.
        if (timer != null) {
            ClockDigits(TimerRules.digits(left), Modifier.fillMaxWidth(), size = if (compact) 48f else 64f, textAlign = TextAlign.Center)
            Text(when (timer.state) {
                "RUNNING" -> "预计 ${(Instant.ofEpochMilli(now + left).atZone(ZoneId.systemDefault())).format(DateTimeFormatter.ofPattern("HH:mm:ss"))} 响铃"
                "PAUSED" -> "继续后，从剩余时间开始"
                else -> if (timer.mathUnlockEnabled) "完成算术题，关闭响铃" else "进入响铃页面关闭"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        } else if (!compact) Text("留一段时间，做眼前的事", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!capabilities.exact || !capabilities.notifications || !capabilities.fullScreen || !capabilities.alarmOverlay) {
            TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(when {
                    !capabilities.exact -> "允许精确闹钟"
                    !capabilities.notifications -> "通知已关闭 · 检查权限"
                    !capabilities.fullScreen -> "锁屏全屏未授权 · 检查权限"
                    else -> "全局蒙版未授权 · 检查权限"
                },
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimerTimeCard(hour: String, hourChange: (String) -> Unit, minute: String, minuteChange: (String) -> Unit,
    second: String, secondChange: (String) -> Unit, duration: Long, valid: Boolean, compact: Boolean, preset: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("设定时间", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationField(hour, hourChange, "时", Modifier.weight(1f))
                DurationField(minute, minuteChange, "分", Modifier.weight(1f))
                DurationField(second, secondChange, "秒", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 5, 10, 25).forEach { value ->
                    val selected = duration == value * 60_000L
                    OutlinedButton(onClick = { preset(value) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(if (compact) "${value}分" else "$value 分钟", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            if (!valid) Text("请设置 1 秒到 24 小时，分与秒为 0–59", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TimerReminderCard(math: Boolean, mathChange: (Boolean) -> Unit, vibrate: Boolean, vibrateChange: (Boolean) -> Unit,
    compact: Boolean) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("到时提醒", style = MaterialTheme.typography.titleMedium)
            if (compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimerCompactOption("复健", math, mathChange, "计时器复健开关", Modifier.weight(1f))
                    TimerCompactOption("振动", vibrate, vibrateChange, "计时器振动开关", Modifier.weight(1f))
                }
                Text("复健需答对两位数加减题才能关闭", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TimerOption("复健", "答对一道两位数加减题才能关闭", math, mathChange, "计时器复健开关")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                TimerOption("振动", "响铃时同时振动", vibrate, vibrateChange, "计时器振动开关")
            }
        }
    }
}

@Composable
private fun TimerCompactOption(title: String, checked: Boolean, change: (Boolean) -> Unit, description: String,
    modifier: Modifier) {
    Row(modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked, change, modifier = Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun TimerActiveCard(timer: TimerSession, left: Long, error: String?, busy: Boolean, vm: ClockViewModel,
    cancel: () -> Unit, dismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本次计时", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(TimerRules.digits(timer.duration), style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = { 1f - left.toFloat() / timer.duration }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusTag("默认闹钟铃声", Icons.Outlined.MusicNote)
                if (timer.mathUnlockEnabled) StatusTag("复健 · 答题关闭", Icons.Outlined.Calculate)
                if (timer.vibrate) StatusTag("振动", Icons.Outlined.Vibration)
            }
            if (error != null) {
                Text("未排程 · $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::rebuild, enabled = !busy) { Text("重新排程") }
            }
            if (timer.state == "RINGING" || (timer.state == "RUNNING" && left == 0L)) {
                Button(onClick = dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = timer.state == "RINGING") { Text("进入关闭响铃") }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (timer.state == "PAUSED") vm.resumeTimer(timer.id) else vm.pauseTimer(timer.id) },
                    enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(if (timer.state == "PAUSED") Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text(if (timer.state == "PAUSED") "继续计时" else "暂停计时")
                }
                OutlinedButton(onClick = cancel, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("取消计时") }
            }
        }
    }
}

@Composable
private fun DurationField(value: String, change: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(value, { next -> if (next.length <= 2 && next.all { it in '0'..'9' }) change(next) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = ClockNumerals, textAlign = TextAlign.Center),
        modifier = modifier, shape = RoundedCornerShape(14.dp))
}

@Composable
private fun TimerOption(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit, description: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, change, modifier = Modifier.semantics { contentDescription = description })
    }
}

