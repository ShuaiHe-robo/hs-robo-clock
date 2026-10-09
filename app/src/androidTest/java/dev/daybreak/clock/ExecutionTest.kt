package dev.daybreak.clock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.*
import dev.daybreak.clock.domain.MathChallenge
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExecutionTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
    @Test fun staleScheduleIsFencedAndWrongAnswersCannotDismiss() = runBlocking {
        val alarm = Alarm(label = "集成验证", days = 0, mathUnlockEnabled = true)
        app.alarms.save(alarm)
        val old = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
        val updated = app.database.dao().alarm(alarm.id)!!.copy(minute = (alarm.minute + 1) % 60)
        app.alarms.save(updated)
        assertFalse(app.alarms.fire(old.id))
        assertEquals("CANCELLED", app.bootStore.session(old.id)!!.state)
        val pending = app.bootStore.allSessions().first { it.alarmId == alarm.id && it.state == "PENDING" }
        val due = pending.copy(scheduledAt = System.currentTimeMillis() - 1000)
        app.bootStore.putSession(due)
        assertTrue(app.alarms.fire(due.id))
        assertTrue(app.alarms.fire(due.id))
        assertEquals(1, app.bootStore.allSessions().count { it.id == due.id && it.state == "RINGING" })
        val q = MathChallenge(due.left, due.right, due.subtract)
        assertFalse(app.alarms.dismiss(due.id, (q.answer + 1).toString()))
        assertEquals("RINGING", app.bootStore.session(due.id)!!.state)
        app.alarms.delete(alarm.id)
        assertEquals("RINGING", app.bootStore.session(due.id)!!.state)
        assertTrue(app.alarms.dismiss(due.id, q.answer.toString()))
        assertFalse(app.alarms.fire(due.id))
        val reopened = BootStore(app)
        assertEquals("ENDED", reopened.session(due.id)!!.state)
        assertEquals(due.left, reopened.session(due.id)!!.left)
        assertEquals("ENDED", app.database.dao().observeRinging().first().first { it.id == due.id }.state)
    }

    @Test fun overlappingWindowsSurviveRuleDeletionAndReleaseTargetsOnlyOriginalSessions() = runBlocking {
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val start = (minute + 1438) % 1440
        val end = (minute + 5) % 1440
        val a = FocusRule(id = UUID.randomUUID().toString(), label = "窗口 A", startMinute = start, endMinute = end, days = 127, packages = "dev.test.a")
        val b = a.copy(id = UUID.randomUUID().toString(), label = "窗口 B", packages = "dev.test.a\ndev.test.b")
        app.focus.save(a); app.focus.save(b)
        assertTrue(app.focus.blockedPackages().containsAll(listOf("dev.test.a", "dev.test.b")))
        val token = app.focus.beginRelease()!!
        assertFalse(app.focus.confirmRelease(token))
        assertTrue(app.focus.remaining(token) > 0)
        val c = a.copy(id = UUID.randomUUID().toString(), label = "新窗口", packages = "dev.test.c")
        app.focus.save(c)
        app.focus.delete(a.id); app.focus.delete(b.id)
        assertTrue("dev.test.a" in app.focus.blockedPackages())
        assertTrue(app.focus.sessions.value.filter { it.ruleId == c.id }.all { it.releaseToken == null })
        app.focus.cancelRelease(token)
        assertTrue(app.focus.sessions.value.filter { it.ruleId == a.id || it.ruleId == b.id }.all { it.state == "ACTIVE" })
        val retry = app.focus.beginRelease()!!
        assertTrue(app.focus.remaining(retry) > 59_000)
        // Simulate an elapsed deadline at the persisted business layer, then add a later window.
        app.database.dao().releaseSessions(retry).forEach {
            app.database.dao().saveFocusSession(it.copy(waitElapsed = android.os.SystemClock.elapsedRealtime() - 60_001))
        }
        val d = a.copy(id = UUID.randomUUID().toString(), label = "后开始窗口", packages = "dev.test.d")
        app.focus.save(d)
        assertTrue(app.focus.confirmRelease(retry))
        assertEquals(setOf("dev.test.d"), app.focus.blockedPackages().filter { it.startsWith("dev.test.") }.toSet())
        assertTrue(app.database.dao().releaseSessions(retry).all { it.state == "EMERGENCY_RELEASED" && it.releasedAt != null })
        app.focus.delete(c.id)
        app.focus.delete(d.id)
        // Clean test sessions without waiting for actual wall time.
        app.focus.sessions.value.filter { it.ruleId in listOf(a.id, b.id, c.id, d.id) }.forEach { app.database.dao().saveFocusSession(it.copy(state = "EXPIRED")) }
        app.focus.refresh()
    }
}
