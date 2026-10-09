package dev.daybreak.clock

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZonedDateTime
import java.util.UUID

/** Verify future/active summaries and that emergency release remains reachable after the layout change. */
@RunWith(AndroidJUnit4::class)
class CountdownOverviewTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun flatten(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { flatten(node.getChild(it)) }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val window = automation.windows.sortedByDescending { it.layer }
            .firstOrNull { it.root?.packageName?.toString() == "dev.daybreak.clock" }
        return flatten(window?.root ?: automation.rootInActiveWindow)
    }
    private fun awaitNode(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val until = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < until) {
            nodes().firstOrNull(match)?.let { return it }
            Thread.sleep(100)
        }
        error("Expected overview control was not shown")
    }
    private fun click(text: String) {
        awaitNode { it.text?.toString() == text }
        for (candidate in nodes().filter { it.text?.toString() == text }) {
            var target: AccessibilityNodeInfo? = candidate
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        }
        error("No clickable control: $text")
    }
    private fun screenshot(name: String) {
        Thread.sleep(300)
        val app = instrumentation.targetContext.applicationContext as ClockApplication
        val bitmap = requireNotNull(automation.takeScreenshot())
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun futureAndActiveFocusCountdownsKeepReleaseActionsUsable() {
        val app = instrumentation.targetContext.applicationContext as ClockApplication
        val stamp = UUID.randomUUID().toString()
        val now = ZonedDateTime.now()
        fun minute(value: ZonedDateTime) = value.hour * 60 + value.minute
        val future = FocusRule(id = "countdown-$stamp", label = "专注倒计时验证",
            startMinute = minute(now.plusHours(2)), endMinute = minute(now.plusHours(3)), days = 127,
            packages = "dev.daybreak.clock.countdown.target")
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitNode { it.text?.toString()?.contains("距离响铃还有") == true }
            assertFalse(nodes().any { it.contentDescription?.toString() == "打开设置" })
            screenshot("emulator-alarm-countdown")
            runBlocking(Dispatchers.IO) { app.focus.save(future) }
            click("专注")
            awaitNode { it.text?.toString()?.contains("距离专注开始还有") == true }
            screenshot("emulator-focus-upcoming")
            runBlocking(Dispatchers.IO) {
                app.focus.save(future.copy(startMinute = minute(now.minusMinutes(5)), endMinute = minute(now.plusMinutes(30))))
            }
            awaitNode { it.text?.toString()?.contains("距离全部结束还有") == true }
            awaitNode { it.text?.toString() == "应急解除 · 等待 60 秒" }
            screenshot("emulator-focus-active")
            click("应急解除 · 等待 60 秒")
            val confirm = awaitNode { it.text?.toString() == "确认解除" }
            var control: AccessibilityNodeInfo? = confirm
            while (control != null && !control.isClickable) control = control.parent
            assertNotNull(control)
            assertFalse(control!!.isEnabled)
            click("取消等待")
            awaitNode { it.text?.toString() == "应急解除 · 等待 60 秒" }
        } finally {
            scenario.close()
            runBlocking(Dispatchers.IO) {
                app.focus.delete(future.id)
                app.database.dao().protectedFocusSessions().filter { it.ruleId == future.id }.forEach {
                    app.database.dao().saveFocusSession(it.copy(state = "EXPIRED", releaseToken = null))
                }
                app.focus.refresh()
            }
        }
    }
}
