package dev.daybreak.clock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.daybreak.clock.R

internal data class LandscapeGeometry(val heightFactor: Float, val sunX: Float, val sunSize: Float, val sunHorizon: Float)

private val LandscapeGeometryConverter = TwoWayConverter<LandscapeGeometry, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.heightFactor, it.sunX, it.sunSize, it.sunHorizon) },
    convertFromVector = { LandscapeGeometry(it.v1, it.v2, it.v3, it.v4) }
)
private data class HillBlend(val alarm: Float, val focus: Float, val timer: Float, val settings: Float)
private val HillBlendConverter = TwoWayConverter<HillBlend, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.alarm, it.focus, it.timer, it.settings) },
    convertFromVector = { HillBlend(it.v1, it.v2, it.v3, it.v4) }
)

@Composable
internal fun rememberLandscapeGeometry(selectedTab: Int): LandscapeGeometry {
    val target = landscapeGeometryForTab(selectedTab)
    // A direct animation keeps the specified tween when retargeted. Transition substitutes
    // a default spring on interruption, which makes this deliberately slow landscape rush.
    // One vector also keeps the sun's placement, size and frame height on the same clock.
    val geometry by animateValueAsState(target, LandscapeGeometryConverter,
        animationSpec = tween(760, easing = FastOutSlowInEasing), label = "landscape-geometry")
    return geometry
}

/** One persistent landscape connects the tabs; content scrolls behind its organic silhouette. */
@Composable
fun LandscapeFrame(selectedTab: Int, modifier: Modifier = Modifier, content: @Composable (Dp) -> Unit) {
    val geometry = rememberLandscapeGeometry(selectedTab)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val sceneHeight = minOf(maxWidth * .50f, maxHeight * .32f, 240.dp)
        // The timer is a dashboard: reserve a smaller, complete horizon below its controls.
        val timerFraction = when {
            maxWidth >= 560.dp && maxHeight < 400.dp -> .30f
            else -> .25f
        }
        val timerHeight = minOf(sceneHeight, maxHeight * timerFraction, 180.dp)
        val baseHeight by animateDpAsState(if (selectedTab == 2) timerHeight else sceneHeight,
            animationSpec = tween(760, easing = FastOutSlowInEasing), label = "landscape-frame-height")
        val height = baseHeight * geometry.heightFactor
        val lowerBy = minOf(40.dp, height * .2f)
        // Reserve the tallest scene while switching so a visible last item remains reachable.
        val clearance = if (selectedTab == 2) baseHeight * .80f + 12.dp
            else maxOf(110.dp, baseHeight - minOf(40.dp, baseHeight * .2f) + 20.dp)
        Box(Modifier.align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .widthIn(max = 720.dp).fillMaxSize()) { content(clearance) }
        LandscapeArtwork(geometry, selectedTab, lowerBy,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height))
    }
}

@Composable
private fun LandscapeArtwork(geometry: LandscapeGeometry, selectedTab: Int, lowerBy: Dp, modifier: Modifier) {
    val blend by animateValueAsState(
        when (selectedTab) { 0 -> HillBlend(1f, 0f, 0f, 0f); 1 -> HillBlend(0f, 1f, 0f, 0f); 2 -> HillBlend(0f, 0f, 1f, 0f); else -> HillBlend(0f, 0f, 0f, 1f) },
        HillBlendConverter, animationSpec = tween(520, easing = FastOutSlowInEasing), label = "hills-blend"
    )
    BoxWithConstraints(modifier.clipToBounds()) {
        val sun = landscapeSunPlacement(maxWidth.value, maxHeight.value, geometry, lowerBy.value)
        Image(painterResource(R.drawable.scenic_sun), null,
            modifier = Modifier.offset {
                IntOffset(sun.left.dp.roundToPx(), sun.top.dp.roundToPx())
            }.size(sun.diameter.dp))
        val hills = listOf(R.drawable.scenic_hills_alarm, R.drawable.scenic_hills_focus, R.drawable.scenic_hills_timer, R.drawable.scenic_hills_settings)
        hills.forEachIndexed { index, resource ->
            val opacity = when (index) { 0 -> blend.alarm; 1 -> blend.focus; 2 -> blend.timer; else -> blend.settings }
            val shift by animateFloatAsState((index - selectedTab).coerceIn(-1, 1) * 12f,
                animationSpec = tween(760, easing = FastOutSlowInEasing), label = "hills-shift-$index")
            Image(painterResource(resource), null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = opacity
                    translationX = shift.dp.toPx()
                })
        }
    }
}
