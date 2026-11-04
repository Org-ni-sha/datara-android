package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `public.user_model_state`: backup of the per-user local layer (residual correction), so a
 * reinstall does not restart personalization from zero.
 */
@Entity(tableName = "user_model_state")
data class UserModelState(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    /** An [ActiveModel] value. */
    @ColumnInfo(name = "active_model") val activeModel: String = ActiveModel.GLOBAL,
    /** The residual correction as a JSON object (`jsonb`). */
    val parameters: String = "{}",
    /** The phone it was learned on; not part of the key. */
    @ColumnInfo(name = "device_id") val deviceId: String?,
    @ColumnInfo(name = "hours_seen") val hoursSeen: Int = 0,
    @ColumnInfo(name = "global_recent_mae") val globalRecentMae: Double?,
    @ColumnInfo(name = "local_recent_mae") val localRecentMae: Double?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)
