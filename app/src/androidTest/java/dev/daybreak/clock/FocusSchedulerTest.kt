package dev.daybreak.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.UiAutomation
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.domain.execution.RecoveryReason
import dev.daybreak.clock.platform.focus.FocusBoundaryReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FocusSchedulerTest {
    @Test fun recoveryMigratesLegacyBoundaryAndRearmsEvenWhenDeadlineDidNotChange() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ClockApplication
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = app.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val rule = FocusRule(label = "专注排程重构验证", startMinute = (minute + 10) % 1440,
            endMinute = (minute + 20) % 1440, days = 127, packages = "dev.test.focus.scheduler")
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        fun boundary() = PendingIntent.getBroadcast(app, 2002, Intent(app, FocusBoundaryReceiver::class.java)
            .setAction("FOCUS_BOUNDARY").setData(Uri.parse("daybreak://focus/boundary")), flags)
        fun registered(): Boolean {
            val dump = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys alarm"))
                .use { it.readBytes().toString(Charsets.UTF_8) }.substringBefore("  App Alarm history:")
            return Regex("RTC_WAKEUP #\\d+: [^\\n]* dev\\.daybreak\\.clock\\}\\r?\\n\\s*tag=\\*walarm\\*:FOCUS_BOUNDARY\\s*", RegexOption.MULTILINE).containsMatchIn(dump)
        }
        try {
            app.focus.save(rule)
            val at = requireNotNull(app.focus.boundary.value.at)
            val legacy = PendingIntent.getBroadcast(app, 2002, Intent(app, FocusBoundaryReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, legacy)
            app.focus.recover(RecoveryReason.PACKAGE_REPLACED)
            assertNull(PendingIntent.getBroadcast(app, 2002, Intent(app, FocusBoundaryReceiver::class.java), flags))
            assertTrue("A migrated boundary must be actually registered with Android", registered())
            manager.cancel(requireNotNull(boundary()))
            assertNotNull("The intent object survives cancellation; existence cannot prove registration", boundary())
            assertFalse(registered())
            app.focus.recover(RecoveryReason.APP_RESUME)
            assertEquals(at, app.focus.boundary.value.at)
            assertTrue("Recovery must rearm a lost entry even if its local deadline matches", registered())
        } finally { app.focus.delete(rule.id) }
    }
}
