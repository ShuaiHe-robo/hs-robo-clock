package dev.daybreak.clock

import android.app.UiAutomation
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.platform.capabilities
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FocusPlatformTest {
    private fun overlayVisible(): Boolean {
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = WindowInspector.getGlobalWindowViews().any { root ->
                val matches = arrayListOf<android.view.View>()
                root.findViewsWithText(matches, "把时间留给此刻", android.view.View.FIND_VIEWS_WITH_TEXT)
                matches.any { it.isShown }
            }
        }
        return found
    }
    @Test fun activeForegroundAppIsCoveredAndNaturalBoundaryRemovesOverlay() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClockApplication
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }
        val enabled = shell("settings get secure enabled_accessibility_services")
        val accessibility = shell("settings get secure accessibility_enabled")
        // Installing/starting instrumentation force-stops the target process. Reconnect the
        // test-only accessibility service after the runner has started, on this emulator only.
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(label = "静默拦截验收", startMinute = (minute + 1439) % 1440,
            endMinute = (minute + 1) % 1440, days = 127, packages = "com.android.chrome")
        try {
            listOf("settings put secure enabled_accessibility_services ''",
                "settings put secure enabled_accessibility_services dev.daybreak.clock/dev.daybreak.clock.platform.focus.FocusAccessibilityService",
                "settings put secure accessibility_enabled 1").forEach { shell(it) }
            withTimeout(8000) { while (!app.capabilities().accessibility) delay(100) }
            val target = requireNotNull(app.packageManager.getLaunchIntentForPackage("com.android.chrome"))
            app.startActivity(target.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            app.focus.save(rule)
            withTimeout(8000) { while (!overlayVisible()) delay(200) }
            assertTrue("com.android.chrome" in app.focus.blockedPackages())
            // Deleting the source must not remove an already active protection window.
            app.focus.delete(rule.id)
            assertTrue(overlayVisible())
            withTimeout(70_000) { while (overlayVisible()) delay(250) }
            assertFalse("com.android.chrome" in app.focus.blockedPackages())
        } finally {
            app.focus.delete(rule.id)
            app.focus.sessions.value.filter { it.ruleId == rule.id }.forEach { app.database.dao().saveFocusSession(it.copy(state = "EXPIRED")) }
            app.focus.refresh()
            if (enabled == "null") shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$enabled'")
            if (accessibility == "null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $accessibility")
        }
    }
}
