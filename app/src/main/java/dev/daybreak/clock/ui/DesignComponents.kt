package dev.daybreak.clock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import dev.daybreak.clock.R

/** Keep time on one line alongside controls at narrow widths and large font scales. */
@Composable
fun ClockDigits(value: String, modifier: Modifier = Modifier, size: Float = 80f,
    color: Color = MaterialTheme.colorScheme.onSurface, textAlign: TextAlign = TextAlign.Start) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val fitted = minOf(size, maxWidth.value / (value.length * .57f) / fontScale)
        Text(value, maxLines = 1, color = color, modifier = Modifier.fillMaxWidth(), textAlign = textAlign, style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = ClockNumerals, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum",
            fontSize = fitted.sp, lineHeight = (fitted * 1.2f).sp, letterSpacing = (-fitted * .025f).sp))
    }
}

/** Names and repeat dates share a scale and baseline, including when they wrap. */
@Composable
fun AlarmMetadata(label: String, schedule: String, labelColor: Color, scheduleColor: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge, color = labelColor)
        Text(schedule, modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge, color = scheduleColor)
    }
}

@Composable
fun MorningArtwork(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.morning_landscape), contentDescription = null, modifier = modifier)
}

/** A full-width horizon: trim the asset's transparent margins at render time. */
@Composable
fun MorningHorizon(modifier: Modifier = Modifier, lowerBy: Dp = 0.dp) {
    Box(modifier.clipToBounds()) {
        Image(painterResource(R.drawable.morning_landscape), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.4f
                scaleY = 1.4f
                translationY = size.height * .11f + lowerBy.toPx()
            })
    }
}

/** Compact horizontal tabs; keep system insets and 48dp selection targets. */
@Composable
fun ClockNavigationBar(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    Surface(color = colors.background) {
        Column {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
            Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    val stacked = maxWidth < 400.dp || fontScale > 1.3f
                    val columns = if (maxWidth.value / fontScale < 240f) 2 else 4
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val labels = listOf("闹钟", "专注", "计时器", "设置")
                        val icons = listOf(Icons.Outlined.Alarm, Icons.Outlined.DoNotDisturbOn, Icons.Outlined.Timer, Icons.Outlined.Tune)
                        labels.indices.toList().chunked(columns).forEach { group ->
                          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                           group.forEach { index ->
                            val label = labels[index]
                            val selected = selectedIndex == index
                            val tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (selected) colors.secondaryContainer else Color.Transparent)
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                                .padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                val tabContent: @Composable () -> Unit = {
                                    Icon(icons[index], null, Modifier.size(22.dp), tint = tint)
                                    Text(label, maxLines = 1, color = tint, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                }
                                if (stacked) Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) { tabContent() }
                                else Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) { tabContent() }
                            }
                        }
                          }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownHeading(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun PageHeading(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeading(title: String, detail: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ListSectionHeading(title: String, detail: String = "", actionLabel: String, onAdd: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val heading: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val action: @Composable () -> Unit = {
        Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = actionLabel },
            shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(actionLabel)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (maxWidth < 320.dp || fontScale > 1.4f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                heading(Modifier.fillMaxWidth())
                Box(Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                heading(Modifier.weight(1f))
                action()
            }
        }
    }
}

@Composable
fun StatusTag(text: String, icon: ImageVector? = null, muted: Boolean = false) {
    Surface(shape = RoundedCornerShape(8.dp),
        color = if (muted) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            icon?.let { Icon(it, null, Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
