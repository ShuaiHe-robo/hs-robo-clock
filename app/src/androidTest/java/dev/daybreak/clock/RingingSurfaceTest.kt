package dev.daybreak.clock

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.data.RingingSession
import dev.daybreak.clock.domain.MathChallenge
import dev.daybreak.clock.domain.execution.ScheduledWake
import dev.daybreak.clock.domain.execution.WakeKey
import dev.daybreak.clock.platform.execution.AndroidExecutionScheduler
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.*
import org.junit.Test
import java.io.File

/** Platform/UI integration: run only on the Pixel emulator started with -no-audio. */
class RingingSurfaceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val app get() = instrumentation.targetContext.applicationContext as ClockApplication
    private val keyguard get() = app.getSystemService(KeyguardManager::class.java)
    private fun shell(command: String): String = instrumentation
        .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        .executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
    private fun emulator() {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        assumeTrue(AndroidExecutionScheduler(app).exactAllowed())
        shell("cmd appops set ${app.packageName} USE_FULL_SCREEN_INTENT allow")
        shell("cmd appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
    }
    private fun nodes(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (node != null) {
            yield(node)
            for (index in 0 until node.childCount) yieldAll(nodes(node.getChild(index)))
        }
    }
    private suspend fun textVisible(value: String) {
        withTimeout(10_000) {
            while (nodes(automation.rootInActiveWindow).none { it.isVisibleToUser && it.text?.toString() == value }) delay(100)
        }
    }
    private suspend fun clickText(value: String) {
        textVisible(value)
        val matches = nodes(automation.rootInActiveWindow).filter { it.isVisibleToUser && (it.text?.toString() == value || it.contentDescription?.toString() == value) }
        val button = matches.mapNotNull { match ->
            var node: AccessibilityNodeInfo? = match
            while (node != null && !node.isClickable) node = node.parent
            node?.takeIf { it.isEnabled }
        }.firstOrNull()
        assertNotNull("No enabled button for $value", button)
        assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        delay(120)
    }
    private suspend fun waitForRinging(id: String): RingingSession {
        withTimeout(20_000) { while (app.bootStore.session(id)?.state != "RINGING") delay(100) }
        withTimeout(10_000) { while (app.alarmReliability.state.value.events.none {
            it.occurrence == id && it.stage == "RINGING_SURFACE_VISIBLE"
        }) delay(100) }
        return app.bootStore.session(id)!!
    }
    private suspend fun ended(id: String) {
        withTimeout(5000) { while (app.bootStore.session(id)?.state != "ENDED") delay(100) }
        withTimeout(5000) { while (app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 1001 }) delay(100) }
    }
    private fun shot(name: String) {
        val bitmap = requireNotNull(instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot())
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun alarmAutomaticallyCoversAnotherAppAndReturnsAfterDismissal() = runBlocking {
        emulator()
        assumeFalse(keyguard.isKeyguardLocked)
        val home = ActivityScenario.launch(MainActivity::class.java)
        val alarm = Alarm(label = "跨应用蒙版验证", days = 0, vibrate = false)
        var occurrence: RingingSession? = null
        try {
            app.alarms.save(alarm)
            occurrence = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
                .copy(scheduledAt = System.currentTimeMillis() + 5000)
            app.bootStore.putSession(occurrence)
            app.database.dao().saveRinging(occurrence)
            assertNull(AndroidExecutionScheduler(app).schedule(ScheduledWake(WakeKey.alarm(occurrence.id), occurrence.scheduledAt)).error)
            shell("am start -a android.settings.SETTINGS")
            waitForRinging(occurrence.id)
            textVisible("跨应用蒙版验证")
            textVisible("关闭闹钟")
            shot("ringing-alarm-other-app")
            clickText("关闭闹钟")
            ended(occurrence.id)
            withTimeout(5000) { while (!Regex("(topResumedActivity|mResumedActivity).*com.android.settings")
                .containsMatchIn(shell("dumpsys activity activities"))) delay(100) }
            assertFalse(keyguard.isKeyguardLocked)
        } finally {
            occurrence?.let { if (app.bootStore.session(it.id)?.state == "RINGING") app.alarms.dismiss(it.id, "") }
            app.alarms.delete(alarm.id)
            home.close()
            shell("cmd appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }

    @Test fun secureLockedTimerUsesBuiltInKeypadAndLeavesDeviceLocked() = lockedTimer(overlay = true, math = true)
    @Test fun lockedTimerFallsBackToSystemFullScreenIntentWithoutOverlayPermission() = lockedTimer(overlay = false, math = false)

    private fun lockedTimer(overlay: Boolean, math: Boolean) = runBlocking {
        emulator()
        assumeFalse("Do not alter a pre-existing emulator credential", keyguard.isDeviceSecure)
        val home = ActivityScenario.launch(MainActivity::class.java)
        var occurrence: RingingSession? = null
        var pinInstalled = false
        try {
            if (!overlay) shell("cmd appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
            assertTrue(shell("locksettings set-pin 2468").contains("set"))
            pinInstalled = true
            shell("settings put secure lockscreen.power_button_instantly_locks 1")
            app.alarms.startTimer(5000, math, false)
            val timer = app.alarms.timers.value.single()
            occurrence = app.bootStore.session(timer.occurrenceId)!!
            shell("input keyevent KEYCODE_HOME")
            shell("input keyevent KEYCODE_SLEEP")
            withTimeout(3000) { while (!keyguard.isKeyguardLocked) delay(50) }
            val ringing = waitForRinging(occurrence.id)
            assertTrue(keyguard.isDeviceSecure)
            assertTrue(keyguard.isKeyguardLocked)
            textVisible("朝醒 · 计时结束")
            if (math) {
                val challenge = MathChallenge(ringing.left, ringing.right, ringing.subtract)
                (challenge.answer + 1).toString().forEach {
                    clickText(it.toString())
                }
                clickText("验证并关闭")
                textVisible("答案不对，再试一次")
                assertEquals("RINGING", app.bootStore.session(ringing.id)!!.state)
                assertTrue(keyguard.isKeyguardLocked)
                shot("ringing-timer-locked-wrong-answer")
                clickText("清空")
                challenge.answer.toString().forEach {
                    clickText(it.toString())
                }
                shot("ringing-timer-locked-keypad")
                clickText("验证并关闭")
            } else {
                shot("ringing-timer-locked-fsi")
                clickText("结束计时")
            }
            ended(ringing.id)
            assertTrue("Closing an alarm must not unlock the phone", keyguard.isKeyguardLocked)
            assertTrue(keyguard.isDeviceSecure)
        } finally {
            occurrence?.let {
                if (app.bootStore.session(it.id)?.state == "RINGING")
                    app.alarms.dismiss(it.id, MathChallenge(it.left, it.right, it.subtract).answer.toString())
                else app.alarms.timers.value.firstOrNull { timer -> timer.occurrenceId == it.id }?.let { timer -> app.alarms.cancelTimer(timer.id) }
            }
            if (pinInstalled) shell("locksettings clear --old 2468")
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            home.close()
            shell("cmd appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
}
