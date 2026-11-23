package com.focusguard.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

/** Reads the list of apps on the phone (names only; nothing is stored or sent). */
class InstalledAppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val labelCache = ConcurrentHashMap<String, String>()

    /** Apps that appear in the app drawer, excluding FocusGuard itself. Slow: call off the main thread. */
    fun launchableApps(): List<InstalledApp> =
        query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, label(it)) }
            .sortedBy { it.label.lowercase() }

    /** Home-screen apps. Never blocked and not counted as "screen time". */
    fun launcherPackages(): Set<String> =
        query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            .map { it.activityInfo.packageName }
            .toSet()

    fun label(packageName: String): String = labelCache.getOrPut(packageName) {
        try {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /** True for apps a user would recognise (has a launcher icon). Hides system services. */
    fun isUserFacing(packageName: String): Boolean =
        pm.getLaunchIntentForPackage(packageName) != null

    private fun query(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
}
