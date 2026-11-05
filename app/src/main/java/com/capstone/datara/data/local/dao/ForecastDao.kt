package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capstone.datara.data.local.entity.Forecast
import kotlinx.coroutines.flow.Flow

/** `forecasts`: the hourly prediction log (D6, E3). */
@Dao
interface ForecastDao {

    /** Logs one hour's forecast; returns -1 when that hour of the cycle is already logged. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(forecast: Forecast): Long

    /** The forecast logged for [predictedAt] (an hour start), or null. */
    @Query("SELECT * FROM forecasts WHERE cycle_id = :cycleId AND predicted_at = :predictedAt")
    suspend fun at(cycleId: String, predictedAt: Long): Forecast?

    /** The cycle's most recent forecast, or null before the first one. */
    @Query("SELECT * FROM forecasts WHERE cycle_id = :cycleId ORDER BY predicted_at DESC LIMIT 1")
    fun observeLatest(cycleId: String): Flow<Forecast?>

    /** The cycle's forecasts made in [from, until), oldest first. */
    @Query(
        "SELECT * FROM forecasts WHERE cycle_id = :cycleId " +
            "AND predicted_at >= :from AND predicted_at < :until ORDER BY predicted_at"
    )
    suspend fun between(cycleId: String, from: Long, until: Long): List<Forecast>

    @Query("SELECT * FROM forecasts WHERE synced_at IS NULL ORDER BY predicted_at LIMIT :limit")
    suspend fun unsynced(limit: Int): List<Forecast>

    @Query("UPDATE forecasts SET synced_at = :syncedAt WHERE forecast_id IN (:ids)")
    suspend fun markSynced(ids: List<String>, syncedAt: Long)
}
