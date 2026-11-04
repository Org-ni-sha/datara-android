package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.datara.data.local.entity.DataUsage
import kotlinx.coroutines.flow.Flow

/** `data_usage`: the 10-minute windows (D6 features, D7 History, E3 actuals). */
@Dao
interface DataUsageDao {

    /** Windows are final when rolled up, so an upsert only happens on a repeated rollup. */
    @Upsert
    suspend fun upsertAll(rows: List<DataUsage>)

    /** The user's windows starting in [from, until), oldest first. */
    @Query(
        "SELECT * FROM data_usage WHERE user_id = :userId " +
            "AND interval_start_at >= :from AND interval_start_at < :until ORDER BY interval_start_at"
    )
    suspend fun between(userId: String, from: Long, until: Long): List<DataUsage>

    @Query(
        "SELECT * FROM data_usage WHERE user_id = :userId " +
            "AND interval_start_at >= :from AND interval_start_at < :until ORDER BY interval_start_at"
    )
    fun observeBetween(userId: String, from: Long, until: Long): Flow<List<DataUsage>>

    /** Start of the device's latest rolled-up window; the next rollup continues after it. */
    @Query("SELECT MAX(interval_start_at) FROM data_usage WHERE device_id = :deviceId")
    suspend fun latestIntervalStart(deviceId: String): Long?

    /** The upload queue, oldest first, in batches of [limit]. */
    @Query("SELECT * FROM data_usage WHERE synced_at IS NULL ORDER BY interval_start_at LIMIT :limit")
    suspend fun unsynced(limit: Int): List<DataUsage>

    @Query("UPDATE data_usage SET synced_at = :syncedAt WHERE data_usage_id IN (:ids)")
    suspend fun markSynced(ids: List<String>, syncedAt: Long)
}
