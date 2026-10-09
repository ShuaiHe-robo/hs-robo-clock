package dev.daybreak.clock

import android.os.Bundle
import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercise the redesigned controls through persistence, recreation and cancel. */
@RunWith(AndroidJUnit4::class)
class EditorWorkflowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun visibleNodes(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val appWindow = automation.windows.sortedByDescending { it.layer }.firstOrNull { it.root?.packageName?.toString() == "dev.daybreak.clock" }
        return nodes(appWindow?.root ?: automation.rootInActiveWindow)
    }

    private fun awaitNode(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            visibleNodes().firstOrNull(match)?.let { return it }
            Thread.sleep(100)
        }
        error("Expected UI control did not appear")
    }

    private fun click(value: String, description: Boolean = false) {
        repeat(8) {
            val all = visibleNodes()
            val candidates = all.filter { it.isVisibleToUser && (if (description) it.contentDescription?.toString() else it.text?.toString()) == value }
            for (candidate in candidates) {
                var node: AccessibilityNodeInfo? = candidate
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { Thread.sleep(250); return }
            }
            val bounds = Rect()
            all.filter { it.isScrollable && !it.isEditable }.maxByOrNull { Rect().also(it::getBoundsInScreen).height() }
                ?.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                val down = SystemClock.uptimeMillis()
                val x = bounds.exactCenterX()
                val start = bounds.top + bounds.height() * .8f
                val end = bounds.top + bounds.height() * .25f
                fun touch(action: Int, y: Float) {
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { automation.injectInputEvent(event, true) } finally { event.recycle() }
                }
                touch(MotionEvent.ACTION_DOWN, start)
                for (step in 1..6) { Thread.sleep(40); touch(MotionEvent.ACTION_MOVE, start + (end - start) * step / 6) }
                touch(MotionEvent.ACTION_UP, end)
            }
            Thread.sleep(300)
        }
        error("Could not activate $value; " + visibleNodes().filter { it.text != null || it.contentDescription != null || it.isScrollable }.joinToString { "${it.text}/${it.contentDescription}/scroll=${it.isScrollable}" })
    }

    private fun setLabel(value: String) {
        val field = awaitNode { it.isEditable }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
    }

    @Test fun alarmShortcutsAndUnlockChoiceSaveAndUnsavedChangesStayLocal() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
        val label = "界面验证-${UUID.randomUUID().toString().take(8)}"
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitNode { it.contentDescription?.toString() == "添加闹钟" }
            click("添加闹钟", description = true)
            setLabel(label)
            click("每天")
            click("复健开关", description = true)
            click("振动", description = true)
            click("保存")
            awaitNode { it.text?.toString() == label }
            val saved = runBlocking { app.database.dao().alarms().single { it.label == label } }
            assertEquals(127, saved.days)
            assertTrue(saved.mathUnlockEnabled)
            assertFalse(saved.vibrate)
            assertEquals("DEFAULT", saved.ringtoneSource)

            click(label)
            setLabel("$label-草稿")
            click("周末")
            scenario.recreate()
            awaitNode { it.isEditable && it.text?.toString() == "$label-草稿" }
            click("返回", description = true)
            val unchanged = runBlocking { app.database.dao().alarm(saved.id)!! }
            assertEquals(label, unchanged.label)
            assertEquals(127, unchanged.days)
        } finally {
            runBlocking { app.database.dao().alarms().filter { it.label.startsWith(label) }.forEach { app.alarms.delete(it.id) } }
            scenario.close()
        }
    }
}
