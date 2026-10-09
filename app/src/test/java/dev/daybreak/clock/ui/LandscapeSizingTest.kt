package dev.daybreak.clock.ui

import org.junit.Assert.*
import org.junit.Test

class LandscapeSizingTest {
    @Test fun entireSunFitsShortLandscapeFramesAndAnimatedIntermediatePositions() {
        for ((width, height) in listOf(720f to 44f, 720f to 64f, 412f to 180f, 320f to 96f)) {
            for (from in 0..3) for (to in 0..3) for (step in 0..10) {
                val a = landscapeGeometryForTab(from); val b = landscapeGeometryForTab(to); val t = step / 10f
                fun mix(x: Float, y: Float) = x + (y - x) * t
                val geometry = LandscapeGeometry(mix(a.heightFactor, b.heightFactor), mix(a.sunX, b.sunX),
                    mix(a.sunSize, b.sunSize), mix(a.sunHorizon, b.sunHorizon))
                val sun = landscapeSunPlacement(width, height, geometry, minOf(40f, height * .2f))
                assertTrue("Sun top cropped at ${width}x$height (page $from->$to): $sun", sun.top >= 0)
                assertTrue("Sun bottom cropped at ${width}x$height: $sun", sun.top + sun.diameter <= height + .001f)
                assertTrue(sun.left >= 0 && sun.left + sun.diameter <= width + .001f)
            }
        }
    }
    @Test fun sunMovesInOneDirectionAndShrinksAcrossTheTabOrder() {
        val pages = (0..3).map(::landscapeGeometryForTab)
        pages.zipWithNext().forEach { (a, b) ->
            assertTrue("Solar path must not reverse at the timer", b.sunX < a.sunX)
            assertTrue(b.sunSize < a.sunSize)
        }
    }
}
