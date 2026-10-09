package dev.daybreak.clock

import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.platform.capabilities
import dev.daybreak.clock.platform.focus.FocusProtectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FocusRecoveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ClockApplication
    private val service = "dev.daybreak.clock/dev.daybreak.clock.platform.focus.FocusAccessibilityService"

    private fun shell(automation: UiAutomation, command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().toString(Charsets.UTF_8).trim()
        }

    private fun overlayVisible(): Boolean {
        var visible = false
        instrumentation.runOnMainSync {
            visible = WindowInspector.getGlobalWindowViews().any { root ->
                val matches = arrayListOf<View>()
                root.findViewsWithText(matches, "把时间留给此刻", View.FIND_VIEWS_WITH_TEXT)
                matches.any { it.isShown }
            }
        }
        return visible
    }

    private suspend fun awaitOverlay(expected: Boolean) {
        repeat(40) {
            if (overlayVisible() == expected) return
            delay(100)
        }
        assertTrue("Expected focus overlay visible=$expected", overlayVisible() == expected)
    }

    @Test fun reconnectCoversAlreadyOpenBlockedAppWithoutAnotherLaunch() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val enabled = shell(automation, "settings get secure enabled_accessibility_services")
        val accessibility = shell(automation, "settings get secure accessibility_enabled")
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(label = "重连回归", startMinute = (minute + 1439) % 1440,
            endMinute = (minute + 5) % 1440, days = 127, packages = "com.android.chrome")
        try {
            shell(automation, "settings put secure enabled_accessibility_services ''")
            delay(500)
            app.focus.save(rule)
            assertTrue("The protection window must already be active", "com.android.chrome" in app.focus.blockedPackages())
            val target = app.packageManager.getLaunchIntentForPackage("com.android.chrome")!!
            app.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(1000)
            // Rebinding can happen after a background process death while the target
            // application is still open. No subsequent app launch should be required.
            shell(automation, "settings put secure enabled_accessibility_services $service")
            shell(automation, "settings put secure accessibility_enabled 1")
            awaitOverlay(true)
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitOverlay(false)
        } finally {
            app.focus.delete(rule.id)
            app.focus.sessions.value.filter { it.ruleId == rule.id }.forEach {
                app.database.dao().saveFocusSession(it.copy(state = "EXPIRED"))
            }
            app.focus.refresh()
            if (enabled == "null") shell(automation, "settings delete secure enabled_accessibility_services")
            else shell(automation, "settings put secure enabled_accessibility_services '$enabled'")
            if (accessibility == "null") shell(automation, "settings delete secure accessibility_enabled")
            else shell(automation, "settings put secure accessibility_enabled $accessibility")
        }
    }

    @Test fun checkedButDisconnectedServiceDoesNotReportWorkingProtection() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val enabled = shell(automation, "settings get secure enabled_accessibility_services")
        val accessibility = shell(automation, "settings get secure accessibility_enabled")
        try {
            shell(automation, "settings put secure enabled_accessibility_services $service")
            shell(automation, "settings put secure accessibility_enabled 1")
            withTimeout(8000) { while (!app.capabilities().accessibility) delay(100) }
            // The test runner can deterministically disconnect services while leaving
            // their configured checkbox intact, reproducing the misleading ready state.
            instrumentation.getUiAutomation()
            delay(500)
            assertTrue(shell(instrumentation.getUiAutomation(),
                "settings get secure enabled_accessibility_services").contains(service))
            assertEquals("A checked service without a running connection cannot block apps",
                FocusProtectionStatus.DISCONNECTED, app.capabilities().focusStatus)
        } finally {
            val restored = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            if (enabled == "null") shell(restored, "settings delete secure enabled_accessibility_services")
            else shell(restored, "settings put secure enabled_accessibility_services '$enabled'")
            if (accessibility == "null") shell(restored, "settings delete secure accessibility_enabled")
            else shell(restored, "settings put secure accessibility_enabled $accessibility")
        }
    }

    @Test fun failedRefreshRecoversWithoutRebindingService() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val enabled = shell(automation, "settings get secure enabled_accessibility_services")
        val accessibility = shell(automation, "settings get secure accessibility_enabled")
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(label = "刷新恢复回归", startMinute = (minute + 1439) % 1440,
            endMinute = (minute + 5) % 1440, days = 127, packages = "com.android.chrome")
        // Bypass validation only in this fixture to force the real refresh path to throw.
        // Removing it then models recovery from a temporary storage/scheduler failure.
        val broken = FocusRule(label = "故障注入", startMinute = 0, endMinute = 0,
            days = 127, packages = "dev.daybreak.clock.test.failure")
        try {
            shell(automation, "settings put secure enabled_accessibility_services ''")
            delay(500)
            shell(automation, "settings put secure enabled_accessibility_services $service")
            shell(automation, "settings put secure accessibility_enabled 1")
            withTimeout(8000) { while (!app.capabilities().accessibility) delay(100) }
            app.focus.save(rule)
            app.startActivity(app.packageManager.getLaunchIntentForPackage("com.android.chrome")!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitOverlay(true)
            app.database.dao().saveFocusRule(broken)
            withTimeout(8000) {
                while (app.capabilities().focusStatus != FocusProtectionStatus.CHECK_FAILED) delay(100)
            }
            // The same failure in the boundary receiver must not crash the process.
            app.sendBroadcast(Intent(app, dev.daybreak.clock.platform.focus.FocusBoundaryReceiver::class.java))
            delay(500)
            app.database.dao().deleteFocusRule(broken.id)
            withTimeout(8000) { while (!app.capabilities().accessibility) delay(100) }
            awaitOverlay(true)
        } finally {
            app.database.dao().deleteFocusRule(broken.id)
            app.focus.delete(rule.id)
            app.focus.sessions.value.filter { it.ruleId == rule.id }.forEach {
                app.database.dao().saveFocusSession(it.copy(state = "EXPIRED"))
            }
            app.focus.refresh()
            if (enabled == "null") shell(automation, "settings delete secure enabled_accessibility_services")
            else shell(automation, "settings put secure enabled_accessibility_services '$enabled'")
            if (accessibility == "null") shell(automation, "settings delete secure accessibility_enabled")
            else shell(automation, "settings put secure accessibility_enabled $accessibility")
        }
    }
}
