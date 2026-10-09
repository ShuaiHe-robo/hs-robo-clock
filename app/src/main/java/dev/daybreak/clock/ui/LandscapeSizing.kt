package dev.daybreak.clock.ui

internal data class SunPlacement(val left: Float, val top: Float, val diameter: Float)

internal fun landscapeGeometryForTab(tab: Int) = when (tab) {
    0 -> LandscapeGeometry(1f, .72f, .34f, .42f)
    1 -> LandscapeGeometry(.88f, .46f, .28f, .74f)
    2 -> LandscapeGeometry(.80f, .34f, .24f, .65f)
    else -> LandscapeGeometry(.72f, .23f, .20f, .59f)
}

internal fun landscapeSunPlacement(width: Float, height: Float, geometry: LandscapeGeometry, lowerBy: Float): SunPlacement {
    val diameter = minOf(width * geometry.sunSize, height * .86f, 136f).coerceAtLeast(0f)
    return SunPlacement((width * geometry.sunX - diameter / 2).coerceIn(0f, (width - diameter).coerceAtLeast(0f)),
        (height * geometry.sunHorizon + lowerBy - diameter / 2).coerceIn(0f, (height - diameter).coerceAtLeast(0f)), diameter)
}
