package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.notifications`: the alert inbox. Each type goes out at most once per cycle, enforced by
 * the unique `(cycle_id, notification_type)` index (as in Postgres, rows without a cycle are not
 * constrained, because SQLite treats NULLs as distinct too).
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["cycle_id", "notification_type"], unique = true),
        Index(value = ["user_id", "sent_at"])
    ]
)
data class Notification(
    @PrimaryKey @ColumnInfo(name = "notification_id") val notificationId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "cycle_id") val cycleId: String?,
    /** A [NotificationType] value. */
    @ColumnInfo(name = "notification_type") val notificationType: String,
    val title: String,
    val message: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    /** null = unread. */
    @ColumnInfo(name = "read_at") val readAt: Long?,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)

/** Values of `notifications.notification_type` (Postgres `notifications_type_valid`). */
object NotificationType {
    const val MILESTONE_75 = "milestone_75"
    const val MILESTONE_90 = "milestone_90"
    const val QUOTA_REACHED = "quota_reached"
    const val FORECAST_24H = "forecast_24h"
    const val FORECAST_3H = "forecast_3h"
}
