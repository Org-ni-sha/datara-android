package com.capstone.datara.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.capstone.datara.data.local.entity.DurableCheckpointEntity
import com.capstone.datara.data.local.entity.DurableReadingEntity
import com.capstone.datara.data.local.entity.TelemetryCheckpointEntity
import com.capstone.datara.data.local.entity.UsageSampleEntity
import kotlinx.coroutines.flow.Flow

/** The phone-only D4 tables. Rows are listed in commit order (`rowid`), oldest first. */
@Dao
interface TelemetryDao {

    // 30-second samples

    @Query("SELECT * FROM telemetry_checkpoint WHERE id = 0")
    suspend fun primaryCheckpoint(): TelemetryCheckpointEntity?

    @Insert
    suspend fun insertSample(sample: UsageSampleEntity)

    @Upsert
    suspend fun upsertPrimaryCheckpoint(checkpoint: TelemetryCheckpointEntity)

    /** A sample and the checkpoint that follows it: both or neither, so no byte counts twice. */
    @Transaction
    suspend fun commitPrimary(sample: UsageSampleEntity, checkpoint: TelemetryCheckpointEntity) {
        insertSample(sample)
        upsertPrimaryCheckpoint(checkpoint)
    }

    /** The most recent [limit] samples, oldest first. */
    @Query("SELECT * FROM usage_samples WHERE rowid IN (SELECT rowid FROM usage_samples ORDER BY rowid DESC LIMIT :limit) ORDER BY rowid")
    fun observeRecentSamples(limit: Int): Flow<List<UsageSampleEntity>>

    /** Samples whose interval ended in [from, until), oldest first: the input of one rollup. */
    @Query("SELECT * FROM usage_samples WHERE interval_end >= :from AND interval_end < :until ORDER BY rowid")
    suspend fun samplesEndingIn(from: Long, until: Long): List<UsageSampleEntity>

    @Query("SELECT MAX(interval_end) FROM usage_samples")
    suspend fun latestSampleEnd(): Long?

    @Query("SELECT COUNT(*) FROM usage_samples WHERE interval_end >= :from")
    suspend fun countSamplesEndingSince(from: Long): Int

    @Query("SELECT MIN(interval_end) FROM usage_samples")
    suspend fun earliestSampleEnd(): Long?

    @Query("DELETE FROM usage_samples WHERE interval_end < :cutoff")
    suspend fun deleteSamplesEndingBefore(cutoff: Long): Int

    // 10-minute durable readings

    @Query("SELECT * FROM durable_checkpoint WHERE id = 0")
    suspend fun durableCheckpoint(): DurableCheckpointEntity?

    @Insert
    suspend fun insertReadings(readings: List<DurableReadingEntity>)

    @Upsert
    suspend fun upsertDurableCheckpoint(checkpoint: DurableCheckpointEntity)

    /** Readings (possibly none) and the checkpoint that follows them, atomically. */
    @Transaction
    suspend fun commitDurable(readings: List<DurableReadingEntity>, checkpoint: DurableCheckpointEntity) {
        if (readings.isNotEmpty()) insertReadings(readings)
        upsertDurableCheckpoint(checkpoint)
    }

    @Query("SELECT MAX(until) FROM durable_readings")
    suspend fun latestReadingUntil(): Long?

    /** The most recent [limit] readings, oldest first. */
    @Query("SELECT * FROM durable_readings WHERE rowid IN (SELECT rowid FROM durable_readings ORDER BY rowid DESC LIMIT :limit) ORDER BY rowid")
    fun observeRecentReadings(limit: Int): Flow<List<DurableReadingEntity>>

    /**
     * Readings overlapping [from, until), oldest first. Used in a cycle (D3) is their sum, with a
     * reading that straddles a boundary split by time (docs/erd-draft.md, tracking_cycles).
     */
    @Query("SELECT * FROM durable_readings WHERE until > :from AND since < :until ORDER BY since, rowid")
    suspend fun readingsOverlapping(from: Long, until: Long): List<DurableReadingEntity>

    /** Live form of [readingsOverlapping] with an open end, for an active cycle's used bytes. */
    @Query("SELECT * FROM durable_readings WHERE until > :from ORDER BY since, rowid")
    fun observeReadingsSince(from: Long): Flow<List<DurableReadingEntity>>
}
