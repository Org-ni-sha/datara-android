package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.forecasts`: the prediction log (D6), one row per active cycle per hour with both models.
 * Log-only: rows are never edited after insert.
 */
@Entity(
    tableName = "forecasts",
    indices = [
        Index(value = ["cycle_id", "predicted_at"], unique = true),
        Index(value = ["user_id", "predicted_at"]),
        Index(value = ["synced_at"])
    ]
)
data class Forecast(
    @PrimaryKey @ColumnInfo(name = "forecast_id") val forecastId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "cycle_id") val cycleId: String,
    /** Start of the hour. */
    @ColumnInfo(name = "predicted_at") val predictedAt: Long,
    @ColumnInfo(name = "remaining_bytes") val remainingBytes: Long,
    /** An [ActiveModel] value: the model the user was shown. */
    @ColumnInfo(name = "active_model") val activeModel: String,
    @ColumnInfo(name = "global_next_hour_bytes") val globalNextHourBytes: Long,
    /** PET of the projection; null when it passes the 30-day cap. */
    @ColumnInfo(name = "global_reach_at") val globalReachAt: Long?,
    /** Both `local_*` stay null until the local layer has learned enough. */
    @ColumnInfo(name = "local_next_hour_bytes") val localNextHourBytes: Long?,
    @ColumnInfo(name = "local_reach_at") val localReachAt: Long?,
    /** The ONNX file that produced the global prediction. */
    @ColumnInfo(name = "model_version") val modelVersion: String,
    /** Feature values as a JSON object (`jsonb`). */
    val inputs: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)

/** Values of `forecasts.active_model` and `user_model_state.active_model`. */
object ActiveModel {
    const val GLOBAL = "global"
    const val LOCAL = "local"
}
