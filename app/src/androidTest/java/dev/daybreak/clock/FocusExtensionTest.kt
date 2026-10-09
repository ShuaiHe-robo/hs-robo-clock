package dev.daybreak.clock

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.clock.data.FocusRule
import dev.daybreak.clock.data.FocusSession
import dev.daybreak.clock.domain.TimeRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FocusExtensionTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ClockApplication
    private val target = "dev.test.focus.extension.a"
    private fun rule(startOffset: Int, endOffset: Int): FocusRule {
        val time = ZonedDateTime.now()
        val minute = time.hour * 60 + time.minute
        return FocusRule(label = "同日延长回归", startMinute = (minute + 1440 + startOffset) % 1440,
            endMinute = (minute + 1440 + endOffset) % 1440, days = 127, packages = target)
    }
    private suspend fun expiredFixture(): Pair<FocusRule, FocusSession> {
        val old = rule(-30, -10)
        val previous = requireNotNull(TimeRules.focusWindow(old.startMinute, old.endMinute, old.days,
            Instant.now().minusSeconds(15 * 60), ZoneId.systemDefault()))
        val snapshot = FocusSession("${old.id}:${previous.start.toEpochMilli()}", old.id, old.label,
            previous.start.toEpochMilli(), previous.end.toEpochMilli(), old.packages, state = "EXPIRED")
        app.database.dao().saveFocusRule(old)
        app.database.dao().saveFocusSession(snapshot)
        val current = old.copy(endMinute = rule(-30, 10).endMinute)
        app.focus.save(current)
        return current to snapshot
    }
    private suspend fun cleanup(ruleId: String) {
        app.database.dao().deleteFocusRule(ruleId)
        app.focus.sessions.value.filter { it.ruleId == ruleId }.forEach {
            app.database.dao().saveFocusSession(it.copy(state = "EXPIRED"))
        }
        app.focus.refresh()
    }
    private suspend fun releaseAfterWait(): String {
        val token = requireNotNull(app.focus.beginRelease())
        assertFalse(app.focus.confirmRelease(token))
        app.database.dao().releaseSessions(token).forEach {
            app.database.dao().saveFocusSession(it.copy(waitElapsed = SystemClock.elapsedRealtime() - 60_001))
        }
        assertTrue(app.focus.confirmRelease(token))
        return token
    }

    @Test fun endedSnapshotDoesNotSuppressAnExtendedCurrentSchedule() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val (current, old) = expiredFixture()
        try {
            assertTrue("Extending an already-ended window must block during the added interval", target in app.focus.blockedPackages())
            val extension = app.focus.sessions.value.single { it.ruleId == current.id }
            val expected = requireNotNull(TimeRules.focusWindow(current.startMinute, current.endMinute, current.days,
                Instant.now(), ZoneId.systemDefault()))
            assertEquals(expected.end.toEpochMilli(), extension.endAt)
            assertTrue(extension.startAt >= old.endAt)
            assertNotEquals(old.id, extension.id)
            assertEquals(old, app.database.dao().focusSession(old.id))
            app.focus.refresh()
            assertEquals("Repeated checks must not duplicate the extension", 1,
                app.focus.sessions.value.count { it.ruleId == current.id })
        } finally { cleanup(current.id) }
    }

    @Test fun activeSnapshotContinuesUnchangedBeforeTheAddedInterval() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val original = rule(-2, 5)
        try {
            app.focus.save(original)
            val first = app.focus.sessions.value.single { it.ruleId == original.id }
            val modified = original.copy(endMinute = rule(-2, 10).endMinute, packages = "$target\ndev.test.focus.extension.b")
            app.focus.save(modified)
            assertEquals(listOf(first), app.focus.sessions.value.filter { it.ruleId == original.id })
            assertFalse("Changes must not alter the current snapshot's targets", "dev.test.focus.extension.b" in app.focus.blockedPackages())
            app.database.dao().saveFocusSession(first.copy(endAt = System.currentTimeMillis() - 1000))
            app.focus.refresh()
            assertTrue("The added interval must use the updated targets", "dev.test.focus.extension.b" in app.focus.blockedPackages())
            assertEquals(1, app.focus.sessions.value.count { it.ruleId == original.id })
        } finally { cleanup(original.id) }
    }

    @Test fun releaseRemainsEffectiveUntilItsOriginalWindowEnds() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val original = rule(-2, 5)
        try {
            app.focus.save(original)
            val token = releaseAfterWait()
            app.focus.save(original.copy(endMinute = rule(-2, 10).endMinute))
            app.focus.refresh()
            assertFalse("Editing must not undo release during its original window", target in app.focus.blockedPackages())
            val released = app.database.dao().releaseSessions(token).single { it.ruleId == original.id }
            app.database.dao().saveFocusSession(released.copy(endAt = System.currentTimeMillis() - 1000))
            app.focus.refresh()
            val extension = app.focus.sessions.value.single { it.ruleId == original.id }
            assertTrue(target in app.focus.blockedPackages())
            assertNull("The later interval must not inherit the old release token", extension.releaseToken)
        } finally { cleanup(original.id) }
    }

    @Test fun releasedExtensionDoesNotReappearFromItsExpiredParent() = runBlocking {
        assumeTrue(Build.MODEL.contains("sdk_gphone"))
        val (current, _) = expiredFixture()
        try {
            assertTrue(target in app.focus.blockedPackages())
            releaseAfterWait()
            app.focus.refresh()
            app.focus.refresh()
            assertFalse("The expired parent must not restart a released extension", target in app.focus.blockedPackages())
        } finally { cleanup(current.id) }
    }
}
