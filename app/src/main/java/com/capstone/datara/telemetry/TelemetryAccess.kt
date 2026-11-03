package com.capstone.datara.telemetry

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What tracking has been granted. Each grant goes through a different Android mechanism. */
data class TrackingAccess(
    /** Special access (Settings → Usage access); needed for the 10-minute NetworkStatsManager read. */
    val usageAccess: Boolean = false,
    /** Runtime permission; gives the 5G/LTE/3G label. Without it the label is CELLULAR. */
    val phoneState: Boolean = false,
    /** Runtime permission on Android 13+, needed to show the tracking notification. */
    val notifications: Boolean = false,
    /** Battery-optimization exemption; lets the watchdog restart tracking from the background. */
    val batteryUnrestricted: Boolean = false
) {
    /** Usage Access and notifications are required to start; the rest improve the data. */
    val canStart get() = usageAccess && notifications
}

/** Reads the current grants; cheap, so the setup screen re-checks it on every resume. */
class TelemetryAccess @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun current() = TrackingAccess(
        usageAccess = hasUsageAccess(),
        phoneState = hasPhoneState(),
        notifications = hasNotifications(),
        batteryUnrestricted = isBatteryUnrestricted()
    )

    /** Usage Access is an app-op, not a runtime permission: checkSelfPermission can't see it. */
    @Suppress("DEPRECATION") // still the documented check for OPSTR_GET_USAGE_STATS
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasPhoneState(): Boolean = granted(Manifest.permission.READ_PHONE_STATE)

    fun hasNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)

    fun isBatteryUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** The runtime permissions the setup screen requests in one dialog sequence. */
        val RUNTIME_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
}
