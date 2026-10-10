package dev.daybreak.clock.platform.focus

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.daybreak.clock.clock
import dev.daybreak.clock.domain.focus.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FocusBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.clock.scope.launch {
            try { if (context.clock.unlocked()) context.clock.focus.boundaryDelivered() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("Daybreak", "Focus boundary refresh failed", e) }
            finally { pending.finish() }
        }
    }
}

/** Window/overlay adapter. Window lifetimes, release waits and retry live in the domain modules. */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: FocusOverlayView? = null
    private var controller: FocusController? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val appLabels = mutableMapOf<String, String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller?.close()
        FocusServiceRuntime.connected()
        controller = FocusController(clock.focus, scope, FocusForeground(::foregroundPackage), FocusOutput(::render),
            checked = { state -> FocusServiceRuntime.checked(when (state) {
                FocusCheck.RUNNING -> null
                FocusCheck.CHECK_FAILED -> FocusProtectionStatus.CHECK_FAILED
                FocusCheck.OUTPUT_UNAVAILABLE -> FocusProtectionStatus.OVERLAY_UNAVAILABLE
            }) }, report = { android.util.Log.e("Daybreak", "Focus check failed; retrying", it) }).also { it.start() }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            controller?.windowChanged(event.packageName?.toString()?.takeUnless { it == packageName && overlay != null })
        }
    }
    private fun foregroundPackage(fallback: String?): String? {
        // Read only the root package; exclude our overlay and the keyboard.
        val candidates = getWindows().filter {
            it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        val current = candidates.firstOrNull { it.isFocused } ?: candidates.firstOrNull { it.isActive }
            ?: candidates.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
        val root = current?.root
        return try { root?.packageName?.toString() ?: fallback }
        finally { @Suppress("DEPRECATION") root?.recycle() }
    }
    private fun render(presentation: FocusPresentation?) {
        if (presentation == null) { removeOverlay(); return }
        if (overlay == null) {
            val view = FocusOverlayView(this,
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME); removeOverlay() },
                onRelease = { controller?.requestRelease() }, onCancel = { controller?.cancelRelease() })
            windowManager.addView(view, WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.OPAQUE))
            overlay = view
        }
        val endText = Instant.ofEpochMilli(presentation.endAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val appLabel = appLabels.getOrPut(presentation.packageName) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(presentation.packageName, 0)).toString() }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { "此应用" }
        }
        overlay?.render(presentation.seconds, endText, appLabel, presentation.releaseToken != null, presentation.waitRemaining)
    }
    private fun removeOverlay() { overlay?.let { runCatching { windowManager.removeView(it) } }; overlay = null }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        removeOverlay(); controller?.windowChanged()
    }
    override fun onInterrupt() { removeOverlay() }
    private fun disconnected() { controller?.close(); controller = null; removeOverlay(); FocusServiceRuntime.disconnected() }
    override fun onUnbind(intent: Intent?): Boolean { disconnected(); return super.onUnbind(intent) }
    override fun onDestroy() { disconnected(); scope.cancel(); super.onDestroy() }
}
