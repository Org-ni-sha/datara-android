package com.capstone.datara.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.capstone.datara.MainActivity
import com.capstone.datara.R
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Android channels and notifications for quota alerts (the tracking ones are in TelemetryNotifications). */
object QuotaAlertNotifications {
    const val MILESTONES_CHANNEL_ID = "quota_milestones"
    const val FORECASTS_CHANNEL_ID = "quota_forecasts"

    const val EXTRA_CYCLE_ID = "com.capstone.datara.extra.CYCLE_ID"
    const val EXTRA_NOTIFICATION_ID = "com.capstone.datara.extra.NOTIFICATION_ID"

    private const val TAG = "QuotaAlertNotifications"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(MILESTONES_CHANNEL_ID, "Quota milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When measured usage reaches 75%, 90% and 100% of your quota. Nothing is blocked."
                },
                NotificationChannel(FORECASTS_CHANNEL_ID, "Forecast alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When your quota is likely to be reached within 24 hours and within 3 hours."
                }
            )
        )
    }

    /** True when Android will show DATAra's notifications (permission granted and not turned off). */
    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(context: Context, alert: Notification) {
        ensureChannels(context)
        if (!canPost(context)) {
            Log.i(TAG, "Notifications not allowed; ${alert.notificationType} kept in the in-app inbox only")
            return
        }
        val channel = if (alert.notificationType in AlertRules.FORECASTS) FORECASTS_CHANNEL_ID else MILESTONES_CHANNEL_ID
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_datara)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setWhen(alert.sentAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(if (alert.notificationType == NotificationType.QUOTA_REACHED) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openCycle(context, alert))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alert.notificationId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing; alert not shown", e)
        }
    }

    /** Opens the app on the alert's cycle; the request code keeps each alert's extras apart. */
    private fun openCycle(context: Context, alert: Notification): PendingIntent = PendingIntent.getActivity(
        context, alert.notificationId.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CYCLE_ID, alert.cycleId)
            .putExtra(EXTRA_NOTIFICATION_ID, alert.notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class AndroidAlertPoster @Inject constructor(@param:ApplicationContext private val context: Context) : AlertPoster {
    override fun post(notification: Notification) = QuotaAlertNotifications.post(context, notification)
}

/** A tapped alert notification, waiting for the signed-in screens to open its cycle. */
data class AlertTap(val notificationId: String, val cycleId: String?)

@Singleton
class AlertNavigation @Inject constructor() {
    private val pending = MutableStateFlow<AlertTap?>(null)
    val tap: StateFlow<AlertTap?> = pending.asStateFlow()

    /** Reads the extras of an intent from [QuotaAlertNotifications]; other intents are ignored. */
    fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(QuotaAlertNotifications.EXTRA_NOTIFICATION_ID) ?: return
        pending.value = AlertTap(id, intent.getStringExtra(QuotaAlertNotifications.EXTRA_CYCLE_ID))
        // Handled once: a configuration change must not re-open it
        intent.removeExtra(QuotaAlertNotifications.EXTRA_NOTIFICATION_ID)
    }

    fun consume() {
        pending.value = null
    }
}
