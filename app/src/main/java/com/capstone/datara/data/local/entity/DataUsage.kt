package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.data_usage`: one row per 10-minute window, rolled up on the phone from its 30-second
 * `usage_samples` (see `UsageRollup`). No `cycle_id`: cycles are found by time window.
 */
@Entity(
    tableName = "data_usage",
    indices = [
        Index(value = ["device_id", "interval_start_at"], unique = true),
        Index(value = ["user_id", "interval_start_at"]),
        Index(value = ["synced_at"])
    ]
)
data class DataUsage(
    @PrimaryKey @ColumnInfo(name = "data_usage_id") val dataUsageId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "interval_start_at") val intervalStartAt: Long,
    @ColumnInfo(name = "interval_end_at") val intervalEndAt: Long,
    @ColumnInfo(name = "bytes_rx") val bytesRx: Long,
    @ColumnInfo(name = "bytes_tx") val bytesTx: Long,
    /** The worst `MeasurementStatus` of the window's samples. */
    @ColumnInfo(name = "measurement_status") val measurementStatus: String,
    @ColumnInfo(name = "network_type") val networkType: String?,
    /** Median of the known values; null when none (the -999 sentinel is never stored here). */
    @ColumnInfo(name = "signal_dbm") val signalDbm: Int?,
    @ColumnInfo(name = "carrier") val carrier: String?,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
) {
    val bytesTotal get() = bytesRx + bytesTx
}
