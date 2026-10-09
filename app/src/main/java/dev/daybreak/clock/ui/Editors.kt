@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.daybreak.clock.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.daybreak.clock.data.*
import dev.daybreak.clock.feature.ClockViewModel
import dev.daybreak.clock.platform.alarm.*
import dev.daybreak.clock.platform.focus.*
import kotlinx.coroutines.*

@Composable
fun AlarmEditor(original: Alarm, existing: Boolean, busy: Boolean, vm: ClockViewModel, close: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hour by rememberSaveable { mutableIntStateOf(original.hour) }
    var minute by rememberSaveable { mutableIntStateOf(original.minute) }
    var days by rememberSaveable { mutableIntStateOf(original.days) }
    var label by rememberSaveable { mutableStateOf(original.label) }
    var vibrate by rememberSaveable { mutableStateOf(original.vibrate) }
    var math by rememberSaveable { mutableStateOf(original.mathUnlockEnabled) }
    var uri by rememberSaveable { mutableStateOf(original.ringtoneUri) }
    var source by rememberSaveable { mutableStateOf(original.ringtoneSource) }
    var name by rememberSaveable { mutableStateOf(original.ringtoneDisplayName) }
    var picking by remember { mutableStateOf(false) }
    var soundError by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val audio = remember { AlarmAudio(context) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { audio.stop(); previewing = false } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { audio.stop(); lifecycle.lifecycle.removeObserver(observer) }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) scope.launch {
            picking = true; soundError = null; audio.stop(); previewing = false
            try {
                val ringtone = RingtoneSelection(context).file(selected)
                uri = ringtone.uri; name = ringtone.name; source = ringtone.source
            } catch (e: Exception) { soundError = e.message }
            finally { picking = false }
        }
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val selected = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (selected != null) {
                uri = selected.toString(); source = "SYSTEM"
                name = runCatching { RingtoneManager.getRingtone(context, selected)?.getTitle(context) }.getOrNull() ?: "系统铃声"
                soundError = null
            }
        }
    }
    EditorFrame(if (existing) "编辑闹钟" else "添加闹钟", busy || picking, close, save = {
        audio.stop()
        vm.saveAlarm(original.copy(hour = hour, minute = minute, days = days, label = label.trim(),
            ringtoneUri = uri, ringtoneSource = source, ringtoneDisplayName = name, vibrate = vibrate, mathUnlockEnabled = math), close)
    }, modifier) {
        TimeField("响铃时间", hour * 60 + minute) { hour = it / 60; minute = it % 60 }
        OutlinedTextField(label, { label = it.take(60) }, label = { Text("闹钟标签") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        SectionLabel("重复")
        DaysPicker(days) { days = it }
        Text(if (days == 0) "仅在下一次到达这个时间时响铃。" else "按所选星期重复。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("唤醒方式")
        ToggleRow("复健开关", "答对一道两位数加减题才能关闭", math) { math = it }
        ToggleRow("振动", "响铃时同时振动", vibrate) { vibrate = it }
        SectionLabel("铃声")
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (picking) "正在验证音频…" else name, style = MaterialTheme.typography.titleMedium)
                Text(if (source == "FILE") "已保存所选文件的读取授权" else "无需选择文件也会正常响铃", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { audio.stop(); previewing = false; systemPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri?.let(Uri::parse))) }, enabled = !picking, modifier = Modifier.heightIn(min = 48.dp)) { Text("系统铃声") }
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, enabled = !picking, modifier = Modifier.heightIn(min = 48.dp)) { Text("音频文件") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (previewing) audio.stop() else audio.play(uri, preview = true) { soundError = "所选铃声不可用，试听已回退" }
                        previewing = !previewing
                    }, enabled = !picking, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(if (previewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(4.dp)); Text(if (previewing) "停止试听" else "试听")
                    }
                    TextButton(onClick = { audio.stop(); previewing = false; uri = null; source = "DEFAULT"; name = "默认闹钟铃声"; soundError = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("恢复默认") }
                }
                soundError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text("不设置贪睡。离开响铃页不会关闭闹钟。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (existing) TextButton(onClick = { deleteConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("删除闹钟") }
    }
    if (deleteConfirm) ConfirmDelete("删除此闹钟？", "取消未来响铃，当前正在响铃的会话仍需正常关闭。", { deleteConfirm = false }) { vm.deleteAlarm(original.id, close) }
}

@Composable
fun FocusEditor(original: FocusRule, existing: Boolean, busy: Boolean, vm: ClockViewModel, close: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var label by rememberSaveable { mutableStateOf(original.label) }
    var start by rememberSaveable { mutableIntStateOf(original.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(original.endMinute) }
    var days by rememberSaveable { mutableIntStateOf(original.days) }
    var packages by rememberSaveable { mutableStateOf(original.packages) }
    var appPicker by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val valid = start != end && days != 0 && packages.isNotBlank()
    EditorFrame(if (existing) "编辑专注计划" else "添加专注计划", busy, close, save = {
        vm.saveFocus(original.copy(label = label.trim().ifBlank { "专注时间" }, startMinute = start, endMinute = end, days = days, packages = packages), close)
    }, modifier = modifier, canSave = valid) {
        OutlinedTextField(label, { label = it.take(60) }, label = { Text("计划名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        BoxWithConstraints {
            if (maxWidth >= 520.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeField("开始", start, Modifier.weight(1f)) { start = it }
                TimeField(if (end < start) "结束 · 次日" else "结束", end, Modifier.weight(1f)) { end = it }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeField("开始", start) { start = it }
                TimeField(if (end < start) "结束 · 次日" else "结束", end) { end = it }
            }
        }
        if (start == end) Text("开始与结束不能相同。", color = MaterialTheme.colorScheme.error)
        SectionLabel("重复")
        DaysPicker(days) { days = it }
        if (days == 0) Text("请至少选择一天。", color = MaterialTheme.colorScheme.error)
        if (end < start) Text("跨午夜窗口按开始日的星期计算。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("限制应用")
        Surface(onClick = { appPicker = true }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (packages.isBlank()) "选择应用" else "已选择 ${packages.split('\n').size} 个应用", style = MaterialTheme.typography.titleMedium)
                    Text("电话、桌面和系统权限入口不参与锁定", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null)
            }
        }
        SectionLabel("应急解除")
        Text("发起后等待 60 秒，再确认一次。等待期间继续拦截；新开始的窗口不属于本次解除范围，未来计划仍会生效。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("已经开始的窗口会继续到期，修改、关闭或删除计划不会提前结束它。延长结束时间将在原窗口结束后接续生效。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (existing) TextButton(onClick = { deleteConfirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("删除计划") }
    }
    if (appPicker) AppPicker(packages.split('\n').filter { it.isNotBlank() }.toSet(), onClose = { appPicker = false }) { packages = it.sorted().joinToString("\n"); appPicker = false }
    if (deleteConfirm) ConfirmDelete("删除此计划？", "当前生效的锁定窗口会继续到期，也可按应急流程解除。", { deleteConfirm = false }) { vm.deleteFocus(original.id, close) }
}

@Composable
private fun EditorFrame(title: String, busy: Boolean, close: () -> Unit, save: () -> Unit, modifier: Modifier, canSave: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                Button(onClick = save, enabled = canSave && !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (busy) "正在保存…" else "保存")
                }
            }
        }
    }
}
@Composable
private fun SectionLabel(value: String) { Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
@Composable
private fun ToggleRow(title: String, helper: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
      Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(helper, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
      }
    }
}
@Composable
fun TimeField(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Surface(onClick = { picker = true }, modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            ClockDigits(timeText(value / 60, value % 60), Modifier.fillMaxWidth(), size = 64f, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("点击调整时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    if (picker) {
        val state = rememberTimePickerState(value / 60, value % 60, true)
        AlertDialog(onDismissRequest = { picker = false }, title = { Text(label) }, text = { TimeInput(state, modifier = Modifier.horizontalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { onChange(state.hour * 60 + state.minute); picker = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("取消") } })
    }
}
@Composable
fun DaysPicker(days: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val fontScale = LocalDensity.current.fontScale
      BoxWithConstraints {
        val dayWidth = maxOf(48f, 14f * fontScale + 16f).dp
        val buttons: @Composable () -> Unit = {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                val chosen = days and (1 shl index) != 0
                Surface(selected = chosen, onClick = { onChange(days xor (1 shl index)) },
                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(min = dayWidth).heightIn(min = 48.dp).semantics { contentDescription = "周$label" }) {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        if (maxWidth >= dayWidth * 7) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { buttons() }
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) { buttons() }
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          listOf("工作日" to 31, "周末" to 96, "每天" to 127, "清除" to 0).forEach { (title, mask) ->
              TextButton(onClick = { onChange(mask) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(title) }
          }
      }
    }
}
@Composable
private fun AppPicker(initial: Set<String>, onClose: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var selected by rememberSaveable { mutableStateOf(initial.joinToString("\n")) }
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { ProtectedApps(context).launchable() } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() } * .9f
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("选择限制的应用", "${selected.split('\n').count { it.isNotBlank() }} 个已选")
            OutlinedTextField(search, { search = it }, label = { Text("搜索应用") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 380.dp)) {
                if (apps == null) item { Text("正在读取可启动应用…", modifier = Modifier.padding(16.dp)) }
                val filtered = apps?.filter { it.name.contains(search, true) || it.packageName.contains(search, true) }.orEmpty()
                if (apps != null && filtered.isEmpty()) item { Text("没有找到应用", modifier = Modifier.padding(16.dp)) }
                items(filtered, key = { it.packageName }) { app ->
                    val set = selected.split('\n').filter { it.isNotBlank() }.toSet()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                        selected = (if (app.packageName in set) set - app.packageName else set + app.packageName).joinToString("\n")
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(app.name); Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Checkbox(app.packageName in set, onCheckedChange = null)
                    }
                }
            }
            Button(onClick = { onSave(selected.split('\n').filter { it.isNotBlank() }.toSet()) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) { Text("确认选择") }
        }
    }
}
@Composable
private fun ConfirmDelete(title: String, description: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { TextButton(onClick = confirm) { Text("删除") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
