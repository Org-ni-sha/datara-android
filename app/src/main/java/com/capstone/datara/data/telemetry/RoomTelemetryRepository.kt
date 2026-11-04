package com.capstone.datara.data.telemetry

import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.entity.DurableCheckpointEntity
import com.capstone.datara.data.local.entity.DurableReadingEntity
import com.capstone.datara.data.local.entity.TelemetryCheckpointEntity
import com.capstone.datara.data.local.entity.UsageSampleEntity
import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.TelemetryCheckpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telemetry in Room (D2). Each sample or set of readings is written in one transaction with the
 * checkpoint that follows it, so after a crash or process death the next tick continues from the
 * last committed checkpoint instead of starting over with a BASELINE.
 *
 * The observe functions show the most recent day, like [InMemoryTelemetryRepository]; older rows
 * stay in the database ([UsageRollup] prunes samples after rolling them up).
 */
@Singleton
class RoomTelemetryRepository @Inject constructor(
    private val dao: TelemetryDao
) : TelemetryRepository {

    override suspend fun primaryCheckpoint(): TelemetryCheckpoint? = dao.primaryCheckpoint()?.toDomain()

    override suspend fun commitPrimary(sample: UsageSample, checkpoint: TelemetryCheckpoint) =
        dao.commitPrimary(UsageSampleEntity.from(sample), TelemetryCheckpointEntity.from(checkpoint))

    override suspend fun durableCheckpoint(): DurableCheckpoint? = dao.durableCheckpoint()?.toDomain()

    override suspend fun commitDurable(readings: List<DurableUsageReading>, checkpoint: DurableCheckpoint) =
        dao.commitDurable(readings.map(DurableReadingEntity::from), DurableCheckpointEntity.from(checkpoint))

    override fun observeSamples(): Flow<List<UsageSample>> =
        dao.observeRecentSamples(InMemoryTelemetryRepository.MAX_SAMPLES).map { rows -> rows.map { it.toDomain() } }

    override fun observeDurableReadings(): Flow<List<DurableUsageReading>> =
        dao.observeRecentReadings(InMemoryTelemetryRepository.MAX_DURABLE_READINGS).map { rows -> rows.map { it.toDomain() } }
}
