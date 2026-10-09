@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.daybreak.clock.ui

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daybreak.clock.clock
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.TimeRules
import dev.daybreak.clock.feature.*
import dev.daybreak.clock.platform.*
import dev.daybreak.clock.platform.alarm.AlarmActivity
import dev.daybreak.clock.platform.focus.FocusProtectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun timeText(hour: Int, minute: Int) = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
fun daysText(days: Int): String = when (days) {
    0 -> "仅一次"; 31 -> "工作日"; 96 -> "周末"; 127 -> "每天"
    else -> (0..6).filter { days and (1 shl it) != 0 }.joinToString(" ") { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[it] }
}
@Composable
fun ClockApp(vm: ClockViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()
    val ringing by vm.ringing.collectAsStateWithLifecycle()
    val active by vm.focus.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var capabilities by remember { mutableStateOf(context.capabilities()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { capabilities = context.capabilities() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { capabilities = context.capabilities(); vm.rebuild() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                capabilities = context.capabilities()
                delay(1000)
            }
        }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    BackHandler(editor != null) { editor = null }
    fun edit(type: String, id: String? = null) { editingId = id; editor = type }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (editor == null) ClockNavigationBar(tab, onSelect = { tab = it })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            val contentModifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 720.dp).fillMaxWidth()
            when (editor) {
                "alarm" -> key(editingId) { AlarmEditor(state.alarms.firstOrNull { it.id == editingId } ?: Alarm(), editingId != null, busy, vm, { editor = null }, contentModifier) }
                "focus" -> key(editingId) { FocusEditor(state.focusRules.firstOrNull { it.id == editingId } ?: FocusRule(), editingId != null, busy, vm, { editor = null }, contentModifier) }
                else -> LandscapeFrame(tab, Modifier.fillMaxWidth()) { bottomClearance ->
                    AnimatedContent(targetState = tab, modifier = Modifier.fillMaxSize(), label = "main-page",
                        transitionSpec = {
                            (fadeIn(tween(200, delayMillis = 120)) togetherWith fadeOut(tween(120)))
                                .using(SizeTransform(clip = false))
                        }) { page ->
                        when (page) {
                            0 -> AlarmList(state, ringing.filterNot { it.isTimer }, capabilities, busy, onEdit = { edit("alarm", it) },
                                onAdd = { edit("alarm") },
                                onToggle = { vm.saveAlarm(it.copy(enabled = !it.enabled)) }, onSettings = { tab = 3 },
                                bottomClearance = bottomClearance, modifier = Modifier.fillMaxSize())
                            1 -> FocusList(state, active, capabilities.focusStatus, busy, vm, onEdit = { edit("focus", it) },
                                onAdd = { edit("focus") },
                                onSettings = { context.openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, bottomClearance = bottomClearance, modifier = Modifier.fillMaxSize())
                            2 -> TimerScreen(vm, capabilities, busy, onSettings = { tab = 3 },
                                bottomClearance = bottomClearance, modifier = Modifier.fillMaxSize())
                            else -> SettingsScreen(capabilities, dark, state, vm, requestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33 && !capabilities.notifications) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else context.openSetting(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            }, bottomClearance = bottomClearance, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmList(state: ClockState, sessions: List<RingingSession>, capabilities: Capabilities, busy: Boolean, onEdit: (String) -> Unit,
    onAdd: () -> Unit, onToggle: (Alarm) -> Unit, onSettings: () -> Unit, bottomClearance: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val next = sessions.filter { it.state == "PENDING" && it.scheduleError == null }.minByOrNull { it.scheduledAt }
    val enabledCount = state.alarms.count { it.enabled }
    val running = sessions.count { it.state == "RINGING" }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
      LazyColumn(modifier.fillMaxHeight(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val title = when {
                state.loading -> "正在读取闹钟…"
                running > 0 -> "正在响铃"
                next != null -> "距离响铃还有\n${countdownText(next.scheduledAt - now)}"
                enabledCount > 0 -> "闹钟尚未排程"
                state.alarms.isEmpty() -> "还没有闹钟"
                else -> "闹钟已全部关闭"
            }
            val detail = when {
                state.loading -> ""
                running > 0 -> "$running 个闹钟等待关闭"
                next != null -> Instant.ofEpochMilli(next.scheduledAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M月d日 EEEE · HH:mm", Locale.CHINA))
                enabledCount > 0 -> "请检查权限或重建待执行计划"
                state.alarms.isEmpty() -> "用下方「添加闹钟」设置时间"
                else -> "${state.alarms.size} 个闹钟 · 开启后显示下次响铃"
            }
            CountdownHeading(title, detail)
        }
        if (!capabilities.exact || !capabilities.notifications || !capabilities.fullScreen) item {
            CapabilityNotice(if (!capabilities.exact) "精确闹钟尚未授权" else if (!capabilities.notifications) "通知已关闭，响铃入口可能无法显示" else "全屏提醒尚未授权，将使用通知入口", onSettings)
        }
        if (running > 0) item {
            Button(onClick = { context.startActivity(android.content.Intent(context, AlarmActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("有 $running 个闹钟正在响铃 · 进入关闭") }
        }
        item {
            ListSectionHeading("你的闹钟", if (state.loading) "" else "${state.alarms.size} 个计划",
                actionLabel = "添加闹钟", onAdd = onAdd)
        }
        if (state.loading) items(2) { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Spacer(Modifier.fillMaxWidth().height(120.dp)) } }
        items(state.alarms, key = { it.id }) { alarm ->
            val pending = sessions.firstOrNull { it.alarmId == alarm.id && it.revision == alarm.revision && it.state == "PENDING" }
            Surface(onClick = { onEdit(alarm.id) }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClockDigits(timeText(alarm.hour, alarm.minute), Modifier.weight(1f), size = 48f,
                            color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = alarm.enabled, onCheckedChange = { onToggle(alarm) }, enabled = !busy,
                            modifier = Modifier.semanticsLabel("${alarm.label.ifBlank { "闹钟" }}开关"))
                    }
                    AlarmMetadata(alarm.label.ifBlank { "闹钟" }, daysText(alarm.days),
                        labelColor = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        scheduleColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!alarm.enabled) StatusTag("已关闭", muted = true)
                        if (alarm.mathUnlockEnabled) StatusTag("复健 · 答题关闭", Icons.Outlined.Calculate, muted = !alarm.enabled)
                        if (alarm.vibrate) StatusTag("振动", Icons.Outlined.Vibration, muted = !alarm.enabled)
                    }
                    if (alarm.enabled && (pending == null || pending.scheduleError != null)) Text("未排程 · ${pending?.scheduleError ?: "请重建计划"}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
      }
}

@Composable
private fun FocusList(state: ClockState, active: List<FocusSession>, focusStatus: FocusProtectionStatus, busy: Boolean, vm: ClockViewModel,
    onEdit: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit, bottomClearance: Dp, modifier: Modifier) {
    val context = LocalContext.current
    val accessibility = focusStatus == FocusProtectionStatus.RUNNING
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val nextFocus = remember(state.focusRules, now / 60_000) {
        state.focusRules.filter { it.enabled }.map { rule ->
            rule to TimeRules.nextFocusStart(rule.startMinute, rule.days, Instant.ofEpochMilli(now), ZoneId.systemDefault()).toEpochMilli()
        }.minByOrNull { it.second }
    }
    LaunchedEffect(Unit) {
        var reportedFailure = false
        while (true) {
            try {
                context.clock.focus.refresh()
                reportedFailure = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!reportedFailure) {
                    vm.report("专注计划刷新失败，请检查运行权限后重建计划")
                    android.util.Log.e("Daybreak", "Focus page refresh failed; retrying", e)
                    reportedFailure = true
                }
            }
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(24.dp, 24.dp, 24.dp, bottomClearance), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageHeading("专注", if (state.loading) "正在读取计划…" else "${state.focusRules.count { it.enabled }} 个计划已启用 · ${state.focusRules.size} 个计划") }
        if (active.isNotEmpty()) item {
            val endAt = active.maxOf { it.endAt }
            val end = Instant.ofEpochMilli(endAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 EEEE · HH:mm", Locale.CHINA))
            CountdownHeading("距离全部结束还有\n${countdownText(endAt - now)}", "$end 结束")
        }
        else if (nextFocus != null) item {
            val start = Instant.ofEpochMilli(nextFocus.second).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 EEEE · HH:mm", Locale.CHINA))
            CountdownHeading("距离专注开始还有\n${countdownText(nextFocus.second - now)}", "${nextFocus.first.label} · $start")
        }
        if (!accessibility) item { CapabilityNotice(focusStatusText(focusStatus), onSettings) }
        if (active.isNotEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.DoNotDisturbOn, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        Text(if (accessibility) "正在专注" else "窗口进行中 · 拦截未运行", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("${active.size} 个窗口 · ${active.flatMap { it.targets() }.toSet().size} 个应用",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    val token = active.firstOrNull { it.state == "RELEASE_PENDING" }?.releaseToken
                    val remaining = token?.let { context.clock.focus.remaining(it) } ?: 0L
                    if (token == null) OutlinedButton(onClick = vm::beginRelease, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("应急解除 · 等待 60 秒") }
                    else {
                        Text(if (remaining > 0) "等待 ${(remaining + 999) / 1000} 秒；期间继续锁定。" else "等待完成，请再次确认。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.confirmRelease(token) }, enabled = remaining == 0L && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("确认解除") }
                            TextButton(onClick = { vm.cancelRelease(token) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消等待") }
                        }
                    }
                    Text("仅解除发起时的窗口，重启会重新等待。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item { ListSectionHeading("定时计划", actionLabel = "添加计划", onAdd = onAdd) }
        if (state.loading) items(2) { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Spacer(Modifier.fillMaxWidth().height(120.dp)) } }
        else if (state.focusRules.isEmpty()) item { EmptyState(Icons.Outlined.EventAvailable, "还没有专注计划", "选择时段和应用，支持跨午夜。") }
        items(state.focusRules, key = { it.id }) { rule ->
            FocusPlanCard(rule, busy, onEdit = { onEdit(rule.id) }, onToggle = { vm.saveFocus(rule.copy(enabled = it)) })
        }
    }
}

@Composable
private fun FocusPlanCard(rule: FocusRule, busy: Boolean, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val timeColor = if (rule.enabled) colors.onSurface else colors.onSurfaceVariant
    val endLabel = if (rule.endMinute < rule.startMinute) "结束 · 次日" else "结束"
    Surface(onClick = onEdit, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(rule.label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp),
                    modifier = Modifier.weight(1f))
                Switch(rule.enabled, onCheckedChange = onToggle, enabled = !busy,
                    modifier = Modifier.semanticsLabel("${rule.label}开关"))
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val start = timeText(rule.startMinute / 60, rule.startMinute % 60)
                val end = timeText(rule.endMinute / 60, rule.endMinute % 60)
                if (fontScale > 1.4f || maxWidth < 260.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FocusTimePoint("开始", start, timeColor, Modifier.fillMaxWidth())
                        FocusTimePoint(endLabel, end, timeColor, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusTimePoint("开始", start, timeColor, Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 22.dp).size(18.dp))
                        FocusTimePoint(endLabel, end, timeColor, Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.outlineVariant.copy(alpha = .55f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                    Text(daysText(rule.days), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Apps, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                    Text("${rule.targets().size} 个应用", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (!rule.enabled) Text("已关闭", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FocusTimePoint(label: String, time: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ClockDigits(time, Modifier.fillMaxWidth(), size = 32f, color = color)
    }
}

private fun focusStatusText(status: FocusProtectionStatus): String = when (status) {
    FocusProtectionStatus.DISABLED -> "无障碍服务未开启，应用锁无法拦截"
    FocusProtectionStatus.DISCONNECTED -> "服务已勾选但未连接，请关闭后重新开启"
    FocusProtectionStatus.CHECK_FAILED -> "拦截检查未正常运行，请重新开启无障碍服务"
    FocusProtectionStatus.OVERLAY_UNAVAILABLE -> "拦截页显示失败，请重新开启无障碍服务"
    FocusProtectionStatus.RUNNING -> "无障碍服务已连接 · 拦截检查正常"
}

@Composable
private fun SettingsScreen(capabilities: Capabilities, dark: Boolean, state: ClockState, vm: ClockViewModel, requestNotifications: () -> Unit,
    bottomClearance: Dp, modifier: Modifier) {
    val context = LocalContext.current
    var openDrawer by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = listOf(capabilities.exact, capabilities.notifications, capabilities.fullScreen, capabilities.accessibility).count { it }
    LazyColumn(modifier.fillMaxHeight(), contentPadding = PaddingValues(24.dp, 24.dp, 24.dp, bottomClearance), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeading("设置") }
        item {
            SettingsEntry("运行权限", if (ready == 4) "$ready / 4 已就绪" else "$ready / 4 已就绪 · ${4 - ready} 项需检查",
                if (ready == 4) Icons.Outlined.CheckCircle else Icons.Outlined.Info) { openDrawer = "permissions" }
        }
        item { SectionHeading("外观与计划") }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("深色界面", style = MaterialTheme.typography.titleMedium); Text("低亮度，更适合睡前使用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(dark, onCheckedChange = vm::theme, modifier = Modifier.semanticsLabel("深色界面开关"))
                }
            }
        }
        item { OutlinedButton(onClick = vm::rebuild, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("重建待执行计划") } }
        item {
            SettingsEntry("使用说明", "后台运行提示与本地数据", Icons.AutoMirrored.Outlined.HelpOutline) { openDrawer = "guide" }
        }
    }
    when (openDrawer) {
        "permissions" -> SettingsDrawer("运行权限", "$ready / 4 已就绪", "关闭权限抽屉", onDismiss = { openDrawer = null }) {
            item { SettingRow("精确闹钟", if (capabilities.exact) "已允许" else "未允许，闹钟无法排程", capabilities.exact) {
                if (Build.VERSION.SDK_INT >= 31) context.openSetting(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            } }
            item { SettingRow("通知", if (capabilities.notifications) "已允许" else "未允许，点击开启", capabilities.notifications, requestNotifications) }
            item { SettingRow("锁屏全屏提醒", if (capabilities.fullScreen) "已允许，系统仍可能展示横幅" else "未允许，将使用通知入口", capabilities.fullScreen) {
                if (Build.VERSION.SDK_INT >= 34) context.openSetting(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            } }
            item { SettingRow("定时应用锁", focusStatusText(capabilities.focusStatus), capabilities.accessibility) {
                context.openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            } }
            item { UsageParagraph("后台运行", "若服务反复断开，请在朝醒的系统应用设置中将电池用量设为「不受限制」，并从三星的休眠、深度休眠应用列表中移除朝醒。") }
            item { OutlinedButton(onClick = { context.openSetting(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("打开朝醒系统设置") } }
        }
        "guide" -> SettingsDrawer("使用说明", "", "关闭说明抽屉", verticalGap = 18.dp, onDismiss = { openDrawer = null }) {
            item { UsageParagraph("三星手机使用提示", "请检查系统的休眠、深度休眠与电池后台限制。侧载 APK 若出现「受限设置」，按系统提示允许后再开启无障碍服务。") }
            item { UsageParagraph("本地与可控", "规则、题目与应急记录只保存在手机。应用锁检测启动后拦截；强停、卸载、撤销权限和修改系统时间会影响运行。电话、桌面与系统权限入口保持可用。") }
            val released = state.history.filter { it.releasedAt != null }.take(5)
            if (released.isNotEmpty()) {
                item { Text("最近应急解除", style = MaterialTheme.typography.titleLarge) }
                items(released) { session ->
                    Text("${Instant.ofEpochMilli(session.releasedAt!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} · ${session.label}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsEntry(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun SettingsDrawer(title: String, detail: String, closeLabel: String, verticalGap: Dp = 0.dp,
    onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() } * .85f
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, closeLabel) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(verticalGap), content = content)
        }
    }
}

@Composable
private fun UsageParagraph(title: String, body: String) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(title: String, description: String, ready: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}
@Composable
private fun CapabilityNotice(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}
@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
private fun Modifier.semanticsLabel(value: String): Modifier = this.then(Modifier.semantics { contentDescription = value })
