package dev.daybreak.clock.platform.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager

data class LaunchableApp(val packageName: String, val name: String)
class ProtectedApps(private val context: Context) {
    private val safe = buildSet {
        add(context.packageName)
        addAll(listOf("com.android.settings", "com.android.systemui", "com.android.phone", "com.android.emergency",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.samsung.android.packageinstaller", "com.samsung.android.dialer",
            "com.samsung.android.app.telephonyui", "com.sec.android.app.launcher"))
        context.getSystemService(TelecomManager::class.java).defaultDialerPackage?.let { add(it) }
        val pm = context.packageManager
        listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))).forEach { intent ->
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { add(it.activityInfo.packageName) }
        }
    }
    fun isProtected(packageName: String) = packageName in safe
    fun launchable(): List<LaunchableApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .filterNot { isProtected(it.activityInfo.packageName) }
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
        .distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
}
