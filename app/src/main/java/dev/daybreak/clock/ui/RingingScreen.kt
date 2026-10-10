package dev.daybreak.clock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.daybreak.clock.R
import dev.daybreak.clock.data.RingingSession
import dev.daybreak.clock.data.isTimer
import dev.daybreak.clock.domain.MathChallenge

/** Claude Design C: an immersive sun, quiet time label and lock-screen-safe controls. */
@Composable
fun RingingScreen(session: RingingSession, count: Int, locked: Boolean,
    dismiss: (String, (Boolean) -> Unit) -> Unit) {
    var answer by rememberSaveable(session.id) { mutableStateOf("") }
    var wrong by rememberSaveable(session.id) { mutableStateOf(false) }
    var submitting by remember(session.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 600.dp && maxHeight < 600.dp
            val fontScale = LocalDensity.current.fontScale
            // Reserve the 48 dp keypad targets and closing controls before sizing the artwork.
            val textGrowth = (fontScale - 1f).coerceAtLeast(0f) * 180f
            val sunHeight = when {
                wide -> (maxHeight.value * .56f).coerceIn(152f, 224f)
                session.mathUnlockEnabled -> (maxHeight.value - 556f - textGrowth -
                    (if (count > 1) 24f else 0f) - (if (session.fallbackUsed) 36f else 0f)).coerceIn(172f, 252f)
                else -> (maxHeight.value * .43f).coerceIn(220f, 348f)
            }.dp
            @Composable fun summary(modifier: Modifier) {
                Column(modifier, horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (session.isTimer) Icons.Outlined.Timer else Icons.Outlined.Alarm, null,
                            Modifier.size(16.dp), tint = colors.primary)
                        Text(if (session.isTimer) "朝醒 · 计时结束" else "朝醒 · 闹钟响铃",
                            style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(sunHeight)) {
                        Image(painterResource(R.drawable.ringing_sun), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterEnd)
                    }
                    Text(session.label.ifBlank { if (session.isTimer) "这一段时间，完成了" else "早安，该起床了" },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                    ClockDigits(if (session.isTimer) "00:00" else timeText(session.hour, session.minute),
                        Modifier.fillMaxWidth().padding(top = 4.dp), size = 32f,
                        color = colors.onSurfaceVariant)
                    if (count > 1) Text("还有 ${count - 1} 个提醒等待关闭", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    if (!session.mathUnlockEnabled) Text(if (session.isTimer) "停一停，准备下一步" else "让今天，从容开始",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            @Composable fun actions(modifier: Modifier) {
                Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (session.mathUnlockEnabled) {
                        val challenge = MathChallenge(session.left, session.right, session.subtract)
                        @Composable fun question(modifier: Modifier) {
                            Text("${challenge.question} = ?",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = ClockNumerals), modifier = modifier)
                        }
                        @Composable fun answerBox(modifier: Modifier) {
                            Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (wrong) colors.error else colors.outline),
                                modifier = modifier.heightIn(min = 56.dp)) {
                                Text(answer.ifBlank { "输入答案" },
                                    style = if (answer.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall.copy(fontFamily = ClockNumerals),
                                    color = if (answer.isEmpty()) colors.onSurfaceVariant else colors.primary, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 300.dp && fontScale <= 1.3f) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    question(Modifier.weight(1f)); answerBox(Modifier.weight(.8f))
                                }
                            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                question(Modifier.fillMaxWidth()); answerBox(Modifier.fillMaxWidth())
                            }
                        }
                        Text(if (wrong) "答案不对，再试一次" else "答对算术题，关闭响铃",
                            style = MaterialTheme.typography.bodySmall, color = if (wrong) colors.error else colors.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
                        // No IME is needed: these keys belong to the showWhenLocked Activity.
                        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "清空", "0", "删除")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            keys.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { digit ->
                                        FilledTonalButton(onClick = {
                                            answer = when (digit) { "清空" -> ""; "删除" -> answer.dropLast(1); else -> (answer + digit).take(5) }
                                            wrong = false
                                        }, enabled = !submitting, shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface),
                                            contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                            if (digit == "删除") Icon(Icons.AutoMirrored.Outlined.Backspace, "删除一位")
                                            else Text(digit, style = if (digit == "清空") MaterialTheme.typography.bodyMedium
                                                else MaterialTheme.typography.titleLarge.copy(fontFamily = ClockNumerals))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        submitting = true
                        dismiss(answer) { accepted -> submitting = false; wrong = !accepted }
                    }, enabled = !submitting && (!session.mathUnlockEnabled || answer.isNotBlank()),
                        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) {
                        Text(if (submitting) "正在关闭…" else if (session.mathUnlockEnabled) "验证并关闭" else if (session.isTimer) "结束计时" else "关闭闹钟",
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Text(if (locked) "关闭提醒后，手机仍保持锁定" else "关闭后，回到刚才的应用",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp))
                    if (session.fallbackUsed) Text("所选铃声不可用，已使用备用铃声。", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
                if (wide) Row(Modifier.widthIn(max = 920.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    summary(Modifier.weight(1f)); actions(Modifier.weight(1f))
                } else Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    summary(Modifier.fillMaxWidth()); actions(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
