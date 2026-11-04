package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** `public.notification_preferences`: the two Settings switches, one row per user. */
@Entity(tableName = "notification_preferences")
data class NotificationPreferences(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    /** Covers `milestone_75`, `milestone_90` and `quota_reached`. */
    @ColumnInfo(name = "milestones_enabled") val milestonesEnabled: Boolean = true,
    /** Covers `forecast_24h` and `forecast_3h`. */
    @ColumnInfo(name = "forecast_alerts_enabled") val forecastAlertsEnabled: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)
