package dev.daybreak.clock.domain.focus

import kotlinx.coroutines.*

fun interface FocusForeground { fun current(fallback: String?): String? }
fun interface FocusOutput { fun render(presentation: FocusPresentation?) }
enum class FocusCheck { RUNNING, CHECK_FAILED, OUTPUT_UNAVAILABLE }

/** Owns checking/retry and output selection; Android supplies window detection and the overlay. */
class FocusController(private val engine: FocusEngine, parentScope: CoroutineScope,
    private val foreground: FocusForeground, private val output: FocusOutput,
    private val checked: (FocusCheck) -> Unit, private val report: (Exception) -> Unit = {}) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private var currentPackage: String? = null
    private var started = false
    private var closed = false

    fun start() {
        if (started || closed) return
        started = true
        scope.launch { engine.sessions.collect { renderSafely() } }
        scope.launch {
            while (isActive) {
                checkOnce()
                delay(1000)
            }
        }
    }
    suspend fun checkOnce() {
        if (closed) return
        try {
            engine.refresh()
            val status = renderSafely()
            if (status == FocusCheck.RUNNING && engine.boundary.value.error != null) checked(FocusCheck.CHECK_FAILED)
            else checked(status)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { checked(FocusCheck.CHECK_FAILED); report(e) }
    }
    fun windowChanged(fallback: String? = null) {
        if (closed) return
        val status = renderSafely(fallback)
        if (status != FocusCheck.RUNNING) checked(status)
    }
    private fun renderSafely(fallback: String? = null): FocusCheck {
        if (closed) return FocusCheck.RUNNING
        val presentation = try {
            currentPackage = foreground.current(fallback) ?: currentPackage
            engine.presentation(currentPackage)
        } catch (e: Exception) { report(e); return FocusCheck.CHECK_FAILED }
        return try { output.render(presentation); FocusCheck.RUNNING }
        catch (e: Exception) { report(e); FocusCheck.OUTPUT_UNAVAILABLE }
    }
    private fun command(block: suspend () -> Unit) {
        if (closed) return
        scope.launch {
            try { block(); windowChanged() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { checked(FocusCheck.CHECK_FAILED); report(e) }
        }
    }
    fun requestRelease() = command {
        val token = engine.presentation(currentPackage)?.releaseToken
        if (token == null) engine.beginRelease() else engine.confirmRelease(token)
    }
    fun cancelRelease() = command {
        engine.presentation(currentPackage)?.releaseToken?.let { engine.cancelRelease(it) }
    }
    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        try { output.render(null) } catch (e: Exception) { report(e) }
        currentPackage = null
    }
}
