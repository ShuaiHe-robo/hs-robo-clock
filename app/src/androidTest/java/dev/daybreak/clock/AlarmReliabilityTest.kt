package dev.daybreak.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.Alarm
import dev.daybreak.clock.data.RingingSession
import dev.daybreak.clock.platform.alarm.AlarmReceiver
import dev.daybreak.clock.platform.alarm.AlarmRingingService
import dev.daybreak.clock.platform.execution.AndroidExecutionScheduler
import dev.daybreak.clock.domain.execution.ScheduledWake
import dev.daybreak.clock.domain.execution.WakeKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmReliabilityTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
    private val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    private fun legacy(session: RingingSession, flags: Int) = PendingIntent.getBroadcast(app, 0,
        Intent(app, AlarmReceiver::class.java).setData(Uri.parse("daybreak://occurrence/${session.id}"))
            .putExtra("occurrence", session.id), flags)

    @Test fun reschedulingMigratesLegacyBroadcastAndCancelRemovesBothIdentities() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        app.alarms.recover()
        val alarm = Alarm(label = "排程迁移验证", days = 127)
        app.alarms.save(alarm)
        val session = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
        val scheduler = AndroidExecutionScheduler(app)
        val old = legacy(session, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!
        app.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(session.scheduledAt, null), old)
        try {
            assertNull(scheduler.schedule(ScheduledWake(WakeKey.alarm(session.id), session.scheduledAt)).error)
            assertNull("Upgrade must remove the broadcast alarm, not leave two callbacks", legacy(session, flags))
            val direct = PendingIntent.getForegroundService(app, 0,
                Intent(app, AlarmRingingService::class.java).setAction("FIRE")
                    .setData(Uri.parse("daybreak://occurrence/${session.id}")).putExtra("occurrence", session.id), flags)
            assertNotNull("AlarmManager should start the ringing service directly", direct)
            scheduler.cancel(WakeKey.alarm(session.id))
            assertNull(legacy(session, flags))
            assertNull(PendingIntent.getForegroundService(app, 0,
                Intent(app, AlarmRingingService::class.java).setAction("FIRE")
                    .setData(Uri.parse("daybreak://occurrence/${session.id}")), flags))
        } finally {
            app.alarms.delete(alarm.id)
            app.getSystemService(AlarmManager::class.java).cancel(old)
            old.cancel()
        }
    }

    @Test fun recoveryMarksMissedAndCancelsItsCallbackWhileKeepingNextRepeat() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        app.alarms.recover()
        val alarm = Alarm(label = "漏响恢复验证", days = 127)
        app.alarms.save(alarm)
        val pending = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
        val missed = pending.copy(scheduledAt = System.currentTimeMillis() - 660_000)
        app.bootStore.putSession(missed)
        app.database.dao().saveRinging(missed)
        val scheduler = AndroidExecutionScheduler(app)
        // An old system entry can outlive the journal deadline after clock changes or restoration.
        assertNull(scheduler.schedule(ScheduledWake(WakeKey.alarm(missed.id), System.currentTimeMillis() + 3_600_000)).error)
        try {
            app.alarms.recover()
            assertEquals("MISSED", app.bootStore.session(missed.id)!!.state)
            assertFalse(app.alarms.fire(missed.id))
            assertNull("Expired occurrences must no longer own a system callback", legacy(missed, flags))
            assertNull(PendingIntent.getForegroundService(app, 0,
                Intent(app, AlarmRingingService::class.java).setAction("FIRE")
                    .setData(Uri.parse("daybreak://occurrence/${missed.id}")), flags))
            val next = app.bootStore.allSessions().single { it.alarmId == alarm.id && it.state == "PENDING" }
            assertTrue(next.scheduledAt > System.currentTimeMillis())
            assertNull(next.scheduleError)
        } finally {
            scheduler.cancel(WakeKey.alarm(missed.id))
            app.alarms.delete(alarm.id)
        }
    }
}
