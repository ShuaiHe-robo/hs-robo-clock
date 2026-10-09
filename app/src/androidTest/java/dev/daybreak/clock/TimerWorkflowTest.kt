package dev.daybreak.clock

import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Use the platform accessibility API: API 37 removed Espresso's InputManager.getInstance. */
class TimerWorkflowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ClockApplication
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun flatten(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { flatten(n.getChild(it)) }
    private fun nodes(): List<AccessibilityNodeInfo> {
        automation.clearCache()
        return flatten(automation.rootInActiveWindow)
    }
    private fun await(predicate: () -> Boolean) {
        val until = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < until) { if (predicate()) return; Thread.sleep(100) }
        error("Timer UI did not reach the expected state: " + nodes().mapNotNull { it.text ?: it.contentDescription })
    }
    private fun click(value: String, description: Boolean = false) {
        repeat(12) {
            for (n in nodes().filter { (if (description) it.contentDescription else it.text)?.toString() == value }) {
                var target: AccessibilityNodeInfo? = n
                while (target != null && !target.isClickable) target = target.parent
                if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { Thread.sleep(250); return }
            }
            nodes().firstOrNull { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(200)
        }
        error("Timer control unavailable: $value")
    }
    private fun screenshot(name: String, top: Boolean = true) {
        if (top) nodes().firstOrNull { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        Thread.sleep(400)
        val image = requireNotNull(automation.takeScreenshot())
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun settingPauseResumeAndCancelSurvivePageSwitches() {
        val capture = InstrumentationRegistry.getArguments().getString("timerCapture")
        val originalDark = runBlocking { app.preferences.dark.first() }
        if (capture != null) runBlocking { app.preferences.setDark(!capture.contains("light")) }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var id: String? = null
        try {
            val landscape = capture?.contains("landscape") == true
            assertTrue(automation.setRotation(if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0))
            await { app.resources.configuration.orientation == if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE
                else android.content.res.Configuration.ORIENTATION_PORTRAIT }
            await { nodes().any { it.text?.toString() == "计时器" } }
            click("计时器")
            await { nodes().any { it.text?.toString() == "设定时间" } }
            if (capture?.contains("large") != true) {
                await { nodes().any { it.text?.toString() == "开始计时" && it.isVisibleToUser } &&
                    nodes().none { it.isScrollable && !it.isEditable } }
                val visible = nodes().filter { it.isVisibleToUser }
                assertFalse("The timer dashboard must fit without a vertical scroll container", visible.any { it.isScrollable && !it.isEditable })
                for (label in listOf("设定时间", "到时提醒", "复健", "振动", "开始计时")) {
                    assertTrue("$label must be visible without scrolling", visible.any { it.text?.toString() == label })
                }
                assertEquals("All duration fields must be visible together", 3, visible.count { it.isEditable })
            }
            screenshot(capture ?: "timer-idle")
            if (capture != null) {
                repeat(10) {
                    if (nodes().any { it.text?.toString() == "开始计时" && it.isVisibleToUser }) {
                        repeat(3) {
                            nodes().firstOrNull { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            Thread.sleep(150)
                        }
                        assertTrue(nodes().any { it.text?.toString() == "开始计时" && it.isVisibleToUser })
                        screenshot("$capture-controls", top = false)
                        if (capture.contains("sequence")) {
                            for ((index, tab) in listOf("闹钟", "专注", "计时器", "设置").withIndex()) {
                                click(tab)
                                Thread.sleep(900)
                                screenshot("$capture-page-$index")
                            }
                        }
                        return
                    }
                    nodes().firstOrNull { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    Thread.sleep(200)
                }
                error("Start timer must remain reachable at this window/font size")
            }
            val field = nodes().filter { it.isEditable }[1]
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "10")
            }))
            click("计时器复健开关", true)
            click("计时器振动开关", true)
            click("开始计时")
            await { app.alarms.timers.value.isNotEmpty() }
            val timer = app.alarms.timers.value.single()
            id = timer.id
            assertEquals(600_000L, timer.duration)
            assertTrue(timer.mathUnlockEnabled)
            assertFalse(timer.vibrate)
            click("暂停计时")
            await { app.bootStore.timer(timer.id)?.state == "PAUSED" }
            val remaining = app.bootStore.timer(timer.id)!!.remaining
            screenshot("timer-paused")
            click("专注"); click("计时器")
            await { nodes().any { it.text?.toString() == "计时已暂停" } }
            assertEquals(remaining, app.bootStore.timer(timer.id)!!.remaining)
            scenario.recreate()
            await { nodes().any { it.text?.toString() == "计时已暂停" } }
            click("继续计时")
            await { app.bootStore.timer(timer.id)?.state == "RUNNING" }
            screenshot("timer-running")
            click("取消计时")
            await { nodes().any { it.text?.toString() == "取消本次计时？" } }
            click("取消计时")
            await { app.alarms.timers.value.isEmpty() }
            await { nodes().any { it.text?.toString() == "设定时间" } }
        } finally {
            id?.let { runBlocking { app.alarms.cancelTimer(it) } }; scenario.close()
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            if (capture != null) runBlocking { app.preferences.setDark(originalDark) }
        }
    }
}

