package dev.daybreak.clock.platform.focus

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.daybreak.clock.clock
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FocusBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.clock.scope.launch {
            try { context.clock.focus.refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.e("Daybreak", "Focus boundary refresh failed", e) }
            finally { pending.finish() }
        }
    }
}

class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundPackage: String? = null
    private var overlay: FocusOverlayView? = null
    private lateinit var safeApps: ProtectedApps
    private var token: String? = null
    private var job: Job? = null
    private var overlayFailed = false
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val appLabels = mutableMapOf<String, String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        safeApps = ProtectedApps(this)
        foregroundPackage = null
        FocusServiceRuntime.connected()
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    withContext(Dispatchers.IO) { clock.focus.refresh() }
                    updateForegroundPackage()
                    render()
                    FocusServiceRuntime.checked(if (overlayFailed) FocusProtectionStatus.OVERLAY_UNAVAILABLE else null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A transient database/window/scheduler failure must not permanently
                    // stop the only loop enforcing the protection window.
                    FocusServiceRuntime.checked(FocusProtectionStatus.CHECK_FAILED)
                    android.util.Log.e("Daybreak", "Focus check failed; retrying", e)
                }
                delay(1000)
            }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::safeApps.isInitialized || event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            try {
                val fallback = event.packageName?.toString()?.takeUnless { it == packageName && overlay != null }
                updateForegroundPackage(fallback)
                render()
            } catch (e: Exception) {
                FocusServiceRuntime.checked(FocusProtectionStatus.CHECK_FAILED)
                android.util.Log.e("Daybreak", "Focus window check failed; retrying", e)
            }
        }
    }
    private fun updateForegroundPackage(fallback: String? = null) {
        // Query only the root's package name. Do not traverse nodes or read text.
        // Excluding our overlay avoids mistaking its own window for the target app.
        val candidates = getWindows().filter {
            it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        val current = candidates.firstOrNull { it.isFocused }
            ?: candidates.firstOrNull { it.isActive }
            ?: candidates.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }
        val root = current?.root
        val detected = try { root?.packageName?.toString() }
        finally { @Suppress("DEPRECATION") root?.recycle() }
        foregroundPackage = detected ?: fallback ?: foregroundPackage
    }
    private fun render() {
        val packageName = foregroundPackage ?: run { removeOverlay(); return }
        val blocked = !safeApps.isProtected(packageName) && packageName in clock.focus.blockedPackages()
        if (!blocked) { removeOverlay(); return }
        if (overlay == null) showOverlay()
        if (overlay == null) return
        val relevant = clock.focus.sessions.value.filter { packageName in it.targets() }
        val end = relevant.maxOfOrNull { it.endAt } ?: return
        val seconds = ((end - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000
        val pending = clock.focus.sessions.value.firstOrNull { it.state == "RELEASE_PENDING" }
        token = pending?.releaseToken
        val remaining = token?.let { clock.focus.remaining(it) } ?: 0
        val endText = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val appLabel = appLabels.getOrPut(packageName) {
            try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { "此应用" }
        }
        overlay?.render(seconds, endText, appLabel, token != null, remaining)
    }
    private fun showOverlay() {
        val scroll = FocusOverlayView(this,
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME); removeOverlay() },
            onRelease = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val current = token
                        if (current == null) clock.focus.beginRelease() else clock.focus.confirmRelease(current)
                    }
                    render()
                }
            },
            onCancel = { token?.let { t -> scope.launch { withContext(Dispatchers.IO) { clock.focus.cancelRelease(t) }; render() } } },
        )
        try {
            windowManager.addView(scroll, WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.OPAQUE))
            overlay = scroll
            overlayFailed = false
        } catch (e: RuntimeException) {
            overlayFailed = true
            FocusServiceRuntime.checked(FocusProtectionStatus.OVERLAY_UNAVAILABLE)
            android.util.Log.e("Daybreak", "Overlay unavailable; retrying", e)
        }
    }
    private fun removeOverlay() { overlay?.let { runCatching { windowManager.removeView(it) } }; overlay = null; overlayFailed = false }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rebuild presentation for live theme, font size, and orientation changes.
        // The active windows and emergency wait continue in the engine.
        removeOverlay()
        if (::safeApps.isInitialized) {
            try { render() }
            catch (e: Exception) {
                FocusServiceRuntime.checked(FocusProtectionStatus.CHECK_FAILED)
                android.util.Log.e("Daybreak", "Focus presentation refresh failed; retrying", e)
            }
        }
    }
    override fun onInterrupt() { removeOverlay() }
    override fun onUnbind(intent: Intent?): Boolean {
        job?.cancel()
        foregroundPackage = null
        removeOverlay()
        FocusServiceRuntime.disconnected()
        return super.onUnbind(intent)
    }
    override fun onDestroy() { job?.cancel(); scope.cancel(); removeOverlay(); FocusServiceRuntime.disconnected(); super.onDestroy() }
}
