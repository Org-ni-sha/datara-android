package com.capstone.datara.data.telemetry

import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.TelemetryCheckpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime telemetry storage: the JVM tests' reference implementation of the contract
 * that [RoomTelemetryRepository] (the one the app binds) also passes. Everything, checkpoints included, is lost when
 * the process dies: the next tick is then a BASELINE and the bytes in between are not counted.
 * Keeps the most recent day of samples so a long-running service cannot grow it without bound.
 */
@Singleton
class InMemoryTelemetryRepository @Inject constructor() : TelemetryRepository {

    private val lock = Mutex()
    private var primary: TelemetryCheckpoint? = null
    private var durable: DurableCheckpoint? = null
    private val samples = MutableStateFlow<List<UsageSample>>(emptyList())
    private val durableReadings = MutableStateFlow<List<DurableUsageReading>>(emptyList())

    override suspend fun primaryCheckpoint(): TelemetryCheckpoint? = lock.withLock { primary }

    override suspend fun commitPrimary(sample: UsageSample, checkpoint: TelemetryCheckpoint) = lock.withLock {
        samples.value = (samples.value + sample).takeLast(MAX_SAMPLES)
        primary = checkpoint
    }

    override suspend fun durableCheckpoint(): DurableCheckpoint? = lock.withLock { durable }

    override suspend fun commitDurable(readings: List<DurableUsageReading>, checkpoint: DurableCheckpoint) = lock.withLock {
        if (readings.isNotEmpty()) durableReadings.value = (durableReadings.value + readings).takeLast(MAX_DURABLE_READINGS)
        durable = checkpoint
    }

    override fun observeSamples(): Flow<List<UsageSample>> = samples.asStateFlow()

    override fun observeDurableReadings(): Flow<List<DurableUsageReading>> = durableReadings.asStateFlow()

    companion object {
        /** One day of 30-second samples. */
        const val MAX_SAMPLES = 2_880

        /** One day of 10-minute readings, plus switch-triggered extras. */
        const val MAX_DURABLE_READINGS = 288
    }
}
