package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `public.tracking_cycles`: one quota the user set, and how it ended.
 *
 * At most one `active` cycle per device. Postgres enforces it with a partial unique index; Room
 * cannot declare partial indices, so `DataraDatabase` adds triggers with the same effect.
 * Reached, then ended by the user: [status] stays `reached` and [endedAt] is set.
 */
@Entity(
    tableName = "tracking_cycles",
    indices = [
        Index(value = ["user_id", "start_at"]),
        Index(value = ["device_id", "status"])
    ]
)
data class TrackingCycle(
    @PrimaryKey @ColumnInfo(name = "cycle_id") val cycleId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val name: String?,
    /** > 0. */
    @ColumnInfo(name = "quota_bytes") val quotaBytes: Long,
    @ColumnInfo(name = "start_at") val startAt: Long,
    /** End of the 10-minute reading in which measured usage crossed [quotaBytes]. */
    @ColumnInfo(name = "reached_at") val reachedAt: Long?,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    /** A [CycleStatus] value. */
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null
)

/** Values of `tracking_cycles.status` (Postgres `tracking_cycles_status_valid`). */
object CycleStatus {
    const val ACTIVE = "active"

    /** Measured usage crossed the quota; `reached_at` is set. */
    const val REACHED = "reached"

    /** Ended by the user before reaching the quota; not scored as a known outcome. */
    const val ENDED = "ended"
}
