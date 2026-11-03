package com.capstone.datara.telemetry

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.capstone.datara.MainActivity
import com.capstone.datara.R

/** Channels and notifications of usage tracking. D8's quota alerts get their own channels. */
object TelemetryNotifications {

    /** The persistent foreground-service notification. Low importance: no sound, no badge. */
    const val TRACKING_CHANNEL_ID = "usage_tracking"

    /** "Tracking paused, tap to resume" when Android refuses a background restart. */
    const val PAUSED_CHANNEL_ID = "usage_tracking_paused"

    const val TRACKING_NOTIFICATION_ID = 1001
    private const val PAUSED_NOTIFICATION_ID = 1002

    private const val TAG = "TelemetryNotifications"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(TRACKING_CHANNEL_ID, "Usage tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while DATAra measures mobile data use."
                    setShowBadge(false)
                },
                NotificationChannel(PAUSED_CHANNEL_ID, "Tracking paused", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when Android stopped usage tracking and it needs the app opened again."
                }
            )
        )
    }

    /** Copy users see constantly: what is measured, and that nothing is blocked. */
    fun tracking(context: Context, measuredBytes: Long): Notification =
        NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_datara)
            .setContentTitle("Measuring mobile data use")
            .setContentText(
                if (measuredBytes > 0) "${formatMegabytes(measuredBytes)} measured since tracking started. Nothing is blocked."
                else "Counts mobile data on this device to forecast your quota. Nothing is blocked."
            )
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun notifyTracking(context: Context, measuredBytes: Long) = post(context, TRACKING_NOTIFICATION_ID, tracking(context, measuredBytes))

    fun notifyPaused(context: Context) = post(
        context, PAUSED_NOTIFICATION_ID,
        NotificationCompat.Builder(context, PAUSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_datara)
            .setContentTitle("Usage tracking paused")
            .setContentText("Android stopped tracking in the background. Open DATAra to resume.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
    )

    /** Decimal megabytes, the quota screens' unit (1 MB = 1,000,000 bytes). */
    fun formatMegabytes(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0)

    private fun post(context: Context, id: Int, notification: Notification) {
        ensureChannels(context)
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing; notification not shown", e)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
