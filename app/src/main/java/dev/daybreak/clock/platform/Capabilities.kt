package dev.daybreak.clock.platform

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.daybreak.clock.platform.focus.FocusAccessibilityService
import dev.daybreak.clock.platform.focus.FocusProtectionStatus
import dev.daybreak.clock.platform.focus.FocusServiceRuntime

data class Capabilities(val exact: Boolean, val notifications: Boolean, val fullScreen: Boolean, val focusStatus: FocusProtectionStatus,
    val batteryExempt: Boolean = false, val backgroundRestricted: Boolean = false) {
    val accessibility: Boolean get() = focusStatus == FocusProtectionStatus.RUNNING
}
fun Context.capabilities() = Capabilities(
    exact = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    notifications = NotificationManagerCompat.from(this).areNotificationsEnabled(),
    fullScreen = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
    focusStatus = FocusServiceRuntime.health.status(
        enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == ComponentName(this, FocusAccessibilityService::class.java) } == true,
        nowElapsed = SystemClock.elapsedRealtime()
    ),
    batteryExempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
    backgroundRestricted = getSystemService(ActivityManager::class.java).isBackgroundRestricted
)
fun Context.openSetting(action: String) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    if (action != Settings.ACTION_ACCESSIBILITY_SETTINGS) intent.data = Uri.parse("package:$packageName")
    try { startActivity(intent) }
    catch (e: RuntimeException) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
