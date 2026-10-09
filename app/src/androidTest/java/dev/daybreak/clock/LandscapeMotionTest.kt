package dev.daybreak.clock

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import dev.daybreak.clock.ui.LandscapeGeometry
import dev.daybreak.clock.ui.rememberLandscapeGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the same remembered geometry as the real frame, with deterministic 16 ms ticks. */
class LandscapeMotionTest {
    @Test fun interruptedSwitchDoesNotAccelerateTheSun() = checkMotion(1f)
    @Test fun slowSystemAnimationsKeepTheSameInterruptionBudget() = checkMotion(5f)
    @Test fun disabledSystemAnimationsReachTheLatestTab() = checkMotion(0f)

    private fun checkMotion(durationScale: Float) = runBlocking(Dispatchers.Main) {
        val clock = BroadcastFrameClock()
        val scale = object : MotionDurationScale { override val scaleFactor = durationScale }
        val recomposer = Recomposer(coroutineContext + clock + scale)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val applier = object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }
        val composition = Composition(applier, recomposer)
        val page = mutableIntStateOf(0)
        var geometry = LandscapeGeometry(1f, .72f, .34f, .42f)
        var time = 0L
        suspend fun frame() {
            Snapshot.sendApplyNotifications()
            repeat(3) { yield() }
            time += 16_000_000L
            clock.sendFrame(time)
            Snapshot.sendApplyNotifications()
            repeat(3) { yield() }
        }
        suspend fun select(tab: Int) {
            page.intValue = tab
            frame()
        }
        suspend fun peakSpeed(frames: Int): Float {
            var previous = geometry.sunX
            var peak = 0f
            repeat(frames) {
                frame()
                val current = geometry.sunX
                peak = maxOf(peak, abs(current - previous) / .016f)
                previous = current
            }
            return peak
        }
        try {
            composition.setContent { geometry = rememberLandscapeGeometry(page.intValue) }
            frame()
            if (durationScale == 0f) {
                for (tab in listOf(2, 1, 0, 2, 0, 1)) {
                    select(tab)
                    repeat(3) { frame() }
                }
                assertEquals(.46f, geometry.sunX, .001f)
                assertEquals(.88f, geometry.heightFactor, .001f)
                return@runBlocking
            }
            val settledFrames = (65 * durationScale).toInt()
            select(3)
            val uninterruptedPeak = peakSpeed(settledFrames)
            assertTrue("The harness must actually advance the slow animation", uninterruptedPeak > .5f / durationScale)
            select(0)
            peakSpeed(settledFrames)
            select(3)
            peakSpeed((28 * durationScale).toInt())
            select(0)
            val interruptedPeak = peakSpeed(settledFrames)
            println("Landscape speed (screen widths/s, scale=$durationScale): uninterrupted=$uninterruptedPeak interrupted=$interruptedPeak")
            assertTrue("An interrupted trip must stay within the slow trip's speed budget: $interruptedPeak vs $uninterruptedPeak",
                interruptedPeak <= uninterruptedPeak * 1.2f + .05f)
            assertEquals("The final requested tab must win", .72f, geometry.sunX, .005f)
            var rapidPeak = 0f
            for ((tab, frames) in listOf(3 to 14, 2 to 7, 1 to 7, 0 to 20, 3 to 38, 1 to 65)) {
                select(tab)
                rapidPeak = maxOf(rapidPeak, peakSpeed((frames * durationScale).toInt()))
                assertTrue("The sun must stay inside the landscape", geometry.sunX in .229f.. .721f)
            }
            assertTrue("Rapid changes must not accelerate beyond the slow trip", rapidPeak <= uninterruptedPeak * 1.2f + .05f)
            assertEquals("The latest of repeated requests must win", .46f, geometry.sunX, .005f)
            assertEquals(.88f, geometry.heightFactor, .005f)
            assertEquals(.28f, geometry.sunSize, .005f)
            assertEquals(.74f, geometry.sunHorizon, .005f)
        } finally {
            composition.dispose()
            recomposer.close()
            job.join()
        }
    }
}
