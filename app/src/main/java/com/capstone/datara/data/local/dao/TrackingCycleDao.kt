package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.capstone.datara.data.local.entity.TrackingCycle
import kotlinx.coroutines.flow.Flow

/**
 * `tracking_cycles` (D3). A second active cycle on the same device is rejected by the database
 * (SQLiteConstraintException), so end or reach the current one first, inside one transaction.
 */
@Dao
interface TrackingCycleDao {

    @Insert
    suspend fun insert(cycle: TrackingCycle)

    /** Callers set `updated_at` to now and `synced_at` to null. */
    @Update
    suspend fun update(cycle: TrackingCycle)

    @Query("SELECT * FROM tracking_cycles WHERE cycle_id = :cycleId")
    suspend fun get(cycleId: String): TrackingCycle?

    @Query("SELECT * FROM tracking_cycles WHERE device_id = :deviceId AND status = 'active'")
    suspend fun active(deviceId: String): TrackingCycle?

    /** The device's active cycle, or null when there is none. */
    @Query("SELECT * FROM tracking_cycles WHERE device_id = :deviceId AND status = 'active'")
    fun observeActive(deviceId: String): Flow<TrackingCycle?>

    /** The user's cycles, newest first (History). */
    @Query("SELECT * FROM tracking_cycles WHERE user_id = :userId ORDER BY start_at DESC")
    fun observeAll(userId: String): Flow<List<TrackingCycle>>

    @Query("SELECT * FROM tracking_cycles WHERE synced_at IS NULL ORDER BY created_at")
    suspend fun unsynced(): List<TrackingCycle>

    @Query(
        "UPDATE tracking_cycles SET synced_at = :syncedAt " +
            "WHERE cycle_id = :cycleId AND updated_at = :updatedAt"
    )
    suspend fun markSynced(cycleId: String, updatedAt: Long, syncedAt: Long)
}
