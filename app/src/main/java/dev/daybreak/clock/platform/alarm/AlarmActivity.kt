package dev.daybreak.clock.platform.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.daybreak.clock.clock
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.data.isTimer
import dev.daybreak.clock.ui.ClockTheme
import dev.daybreak.clock.ui.ClockDigits
import dev.daybreak.clock.ui.MorningArtwork
import dev.daybreak.clock.ui.timeText
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val bars = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        setContent {
            ClockTheme(dark = true) {
                val sessions by clock.alarms.sessions.collectAsStateWithLifecycle()
                val running = sessions.filter { it.state == "RINGING" }
                val session = running.firstOrNull()
                val scope = rememberCoroutineScope()
                var opened by remember { mutableStateOf(false) }
                LaunchedEffect(session?.id) { if (session != null) opened = true else if (opened) finish() }
                BackHandler { moveTaskToBack(true) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(32.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
                        MorningArtwork(Modifier.width(168.dp).height(96.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Alarm, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (session?.isTimer == true) "计时结束" else if (session != null) "正在响铃" else "准备响铃", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(16.dp))
                        if (session != null) ClockDigits(if (session.isTimer) "00:00:00" else timeText(session.hour, session.minute), Modifier.fillMaxWidth(), size = 88f)
                        Text(session?.label?.ifBlank { "新的一天，开始了" } ?: "正在读取会话", style = MaterialTheme.typography.titleLarge)
                        if (running.size > 1) Text("还有 ${running.size - 1} 个闹钟等待关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        if (session != null) key(session.id) {
                            var answer by rememberSaveable { mutableStateOf("") }
                            var wrong by remember { mutableStateOf(false) }
                            var submitting by remember { mutableStateOf(false) }
                            Spacer(Modifier.height(40.dp))
                            if (session.mathUnlockEnabled) {
                                Text("让大脑也醒来", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Text("答对这道题，关闭响铃。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                                    Text("${MathChallenge(session.left, session.right, session.subtract).question} = ?", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(24.dp))
                                }
                                OutlinedTextField(answer, { answer = it.take(8); wrong = false }, label = { Text("你的答案") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, isError = wrong, supportingText = { if (wrong) Text("答案不对，再试一次。") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            }
                            Button(onClick = {
                                submitting = true
                                scope.launch {
                                    val accepted = clock.alarms.dismiss(session.id, answer)
                                    wrong = !accepted
                                    submitting = false
                                }
                            }, enabled = !submitting && (!session.mathUnlockEnabled || answer.isNotBlank()), modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                                Text(if (session.mathUnlockEnabled) "验证并关闭" else "关闭闹钟")
                            }
                            if (session.fallbackUsed) Text("所选铃声不可用，已使用备用铃声。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
}
