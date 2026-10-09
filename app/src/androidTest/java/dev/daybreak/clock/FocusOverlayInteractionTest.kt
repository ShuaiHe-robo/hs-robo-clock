package dev.daybreak.clock

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.platform.capabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FocusOverlayInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ClockApplication
    private val service = "dev.daybreak.clock/dev.daybreak.clock.platform.focus.FocusAccessibilityService"
    private lateinit var automation: UiAutomation

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private fun matching(text: String): List<TextView> {
        val result = mutableListOf<TextView>()
        instrumentation.runOnMainSync {
            WindowInspector.getGlobalWindowViews().forEach { root ->
                val matches = arrayListOf<View>()
                root.findViewsWithText(matches, text, View.FIND_VIEWS_WITH_TEXT)
                result.addAll(matches.filterIsInstance<TextView>().filter { it.isShown })
            }
        }
        return result
    }

    private suspend fun awaitOverlay(visible: Boolean) = withTimeout(8000) {
        while (matching("把时间留给此刻").isNotEmpty() != visible) delay(100)
    }

    private fun click(text: String) {
        val button = matching(text).filterIsInstance<Button>().single()
        instrumentation.runOnMainSync {
            button.requestRectangleOnScreen(Rect(0, 0, button.width, button.height), true)
            assertTrue("Action must be enabled", button.isEnabled)
            assertTrue("Action must be visible", button.getGlobalVisibleRect(Rect()))
            button.performClick()
        }
    }

    private suspend fun screenshot(name: String) {
        delay(500)
        val output = File(app.getExternalFilesDir(null), "focus-overlay/$name.png")
        output.parentFile!!.mkdirs()
        File(output.path).outputStream().use {
            requireNotNull(automation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun homeAndEmergencyActionsPreserveProtectionUntilConfirmation() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val enabled = shell("settings get secure enabled_accessibility_services")
        val accessibility = shell("settings get secure accessibility_enabled")
        val night = shell("cmd uimode night")
        val fontScale = shell("settings get system font_scale")
        val screenSize = shell("wm size")
        val overrideSize = Regex("Override size: (\\d+x\\d+)").find(screenSize)?.groupValues?.get(1)
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(label = "蒙版交互验收", startMinute = (minute + 1439) % 1440,
            endMinute = (minute + 180) % 1440, days = 127, packages = "com.android.chrome")
        fun launchTarget() = app.startActivity(app.packageManager.getLaunchIntentForPackage("com.android.chrome")!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        suspend fun returnHome() { click("返回桌面"); awaitOverlay(false) }
        try {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            shell("cmd uimode night no")
            shell("settings put system font_scale 1.0")
            shell("settings put secure enabled_accessibility_services ''")
            delay(500)
            shell("settings put secure enabled_accessibility_services $service")
            shell("settings put secure accessibility_enabled 1")
            withTimeout(8000) { while (!app.capabilities().accessibility) delay(100) }
            app.focus.save(rule)
            launchTarget()
            awaitOverlay(true)
            screenshot("light-hours")
            assertTrue(matching("时 · 分 · 秒").isNotEmpty())
            assertTrue(matching("Chrome 已暂时锁定").isNotEmpty())

            returnHome()
            assertTrue("Returning home must keep the window active", "com.android.chrome" in app.focus.blockedPackages())
            shell("cmd uimode night yes")
            delay(700)
            launchTarget()
            awaitOverlay(true)
            screenshot("dark-hours")

            click("应急解除")
            withTimeout(8000) { while (app.focus.sessions.value.none { it.state == "RELEASE_PENDING" }) delay(100) }
            delay(1100)
            screenshot("emergency-wait")
            assertTrue("The waiting button must not allow early release", matching("应急解除").filterIsInstance<Button>().single().isEnabled.not())
            assertTrue("Waiting must keep the target blocked", "com.android.chrome" in app.focus.blockedPackages())
            click("取消等待")
            withTimeout(8000) { while (app.focus.sessions.value.any { it.state == "RELEASE_PENDING" }) delay(100) }
            assertTrue("Cancelling must keep protection", "com.android.chrome" in app.focus.blockedPackages())

            withTimeout(8000) { while (app.focus.sessions.value.any { it.state == "RELEASE_PENDING" }) delay(100) }
            // Change font size while the blocking surface is on screen, without relaunching.
            shell("settings put system font_scale 1.6")
            delay(700)
            awaitOverlay(true)
            screenshot("large-font-top")
            click("应急解除")
            withTimeout(8000) { while (app.focus.sessions.value.none { it.state == "RELEASE_PENDING" }) delay(100) }
            delay(1100)
            click("取消等待")
            screenshot("large-font-actions")

            withTimeout(8000) { while (app.focus.sessions.value.any { it.state == "RELEASE_PENDING" }) delay(100) }
            shell("settings put system font_scale 1.0")
            shell("wm size 1920x1080")
            delay(700)
            awaitOverlay(true)
            screenshot("wide-top")
            click("应急解除")
            withTimeout(8000) { while (app.focus.sessions.value.none { it.state == "RELEASE_PENDING" }) delay(100) }
            delay(1100)
            screenshot("wide-actions")
            val pending = app.focus.sessions.value.single { it.ruleId == rule.id }
            // Advance only this emulator fixture's wait clock; exercise the real confirm path.
            app.database.dao().saveFocusSession(pending.copy(waitElapsed = SystemClock.elapsedRealtime() - 61_000))
            app.focus.refresh()
            withTimeout(8000) { while (matching("确认应急解除").isEmpty()) delay(100) }
            click("确认应急解除")
            awaitOverlay(false)
            assertFalse("Confirmation after the wait must release the target", "com.android.chrome" in app.focus.blockedPackages())
        } finally {
            shell("wm size ${overrideSize ?: "reset"}")
            app.focus.delete(rule.id)
            app.focus.sessions.value.filter { it.ruleId == rule.id }.forEach {
                app.database.dao().saveFocusSession(it.copy(state = "EXPIRED"))
            }
            app.focus.refresh()
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            shell("cmd uimode night ${if (night.contains("yes")) "yes" else if (night.contains("auto")) "auto" else "no"}")
            if (fontScale == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $fontScale")
            if (enabled == "null") shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services '$enabled'")
            if (accessibility == "null") shell("settings delete secure accessibility_enabled")
            else shell("settings put secure accessibility_enabled $accessibility")
        }
    }
}
