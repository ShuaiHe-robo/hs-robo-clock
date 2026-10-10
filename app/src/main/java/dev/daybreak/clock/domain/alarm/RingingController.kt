package dev.daybreak.clock.domain.alarm

import dev.daybreak.clock.data.RingingSession
import kotlinx.coroutines.*

/** Android renders notifications/audio; this module owns playback selection and command lifetime. */
interface RingingOutput {
    fun render(session: RingingSession?, newlySelected: Boolean)
    fun finish()
}

class RingingController(
    private val engine: AlarmEngine,
    parentScope: CoroutineScope,
    private val output: RingingOutput,
    private val events: AlarmEvents = NoAlarmEvents
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private var collector: Job? = null
    private var inFlight = 0
    private var selected: String? = null
    private var closed = false
    private var idleSignalled = false

    // Called on the owner dispatcher (Main for the Android service).
    fun deliver(id: String?) {
        if (closed) return
        inFlight++
        idleSignalled = false
        if (collector == null) collector = scope.launch { engine.sessions.collect { render(it) } }
        scope.launch {
            try {
                if (id != null && !engine.fire(id)) events.event("FIRE_REJECTED", id)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { events.event("DELIVERY_FAILED", id, e.toString()) }
            finally { inFlight--; if (!closed) render(engine.sessions.value) }
        }
    }
    private fun render(sessions: List<RingingSession>) {
        if (closed) return
        val next = sessions.firstOrNull { it.lifecycle() == OccurrenceState.RINGING }
        val changed = next?.id != selected
        if (changed || next != null) output.render(next, changed)
        selected = next?.id
        if (next == null && inFlight == 0 && !idleSignalled) {
            idleSignalled = true
            output.finish()
        }
    }
    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        output.render(null, selected != null)
        selected = null
    }
}
