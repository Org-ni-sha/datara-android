package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.devices`: one row per install and account (see `DeviceRepository`). A reinstall creates
 * a new `device_id`. The carrier is not stored here; it is on every `data_usage` row.
 */
@Entity(
    tableName = "devices",
    indices = [Index(value = ["user_id"])]
)
data class Device(
    @PrimaryKey @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    /** `Build.MODEL`. */
    @ColumnInfo(name = "device_model") val deviceModel: String,
    /** `Build.VERSION.SDK_INT` (API level, e.g. 34), not the marketing version. */
    @ColumnInfo(name = "android_sdk_int") val androidSdkInt: Int,
    @ColumnInfo(name = "device_name") val deviceName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)
