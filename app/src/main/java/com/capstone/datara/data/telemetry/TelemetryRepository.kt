package com.capstone.datara.data.telemetry

import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.TelemetryCheckpoint
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * One 30-second TrafficStats interval of mobile usage, with the network inputs the model needs.
 * Field meanings follow data-harvester's `usage_records` (schema version 3).
 */
data class UsageSample(
    /** Generated on the device so sync retries upsert instead of duplicating. */
    val id: String = UUID.randomUUID().toString(),
    /** Wall-clock span the bytes belong to (epoch millis). */
    val intervalStart: Long,
    val intervalEnd: Long,
    /** Monotonic length of the span; -1 when unknown (first reading, after a reboot). */
    val elapsedMs: Long,
    val bytesRx: Long,
    val bytesTx: Long,
    /** A [com.capstone.datara.telemetry.MeasurementStatus] value. */
    val status: String,
    /** Interval label: 5G, LTE, 3G, 2G, CELLULAR, WIFI or NONE (see MeasurementRules.primaryNetworkLabel). */
    val networkType: String,
    /** The default network when the sample was taken, which may differ from [networkType]. */
    val activeNetworkAtSample: String,
    /** Cell signal strength in dBm; RadioLabels.SIGNAL_UNKNOWN when unavailable. */
    val signalDbm: Int,
    /** Carrier of the default data SIM, auto-detected (never user-entered). */
    val carrier: String,
    val utcOffsetMinutes: Int,
    val schemaVersion: Int
) {
    val bytesTotal get() = bytesRx + bytesTx

    /** Average speed over the interval in bytes per second; null when the span is unknown. */
    val bytesPerSecond: Double?
        get() = if (elapsedMs > 0) bytesTotal * 1_000.0 / elapsedMs else null
}

/** Mobile usage between two 10-minute NetworkStatsManager reads: the durable device total. */
data class DurableUsageReading(
    val id: String = UUID.randomUUID().toString(),
    val since: Long,
    val until: Long,
    val bytesRx: Long,
    val bytesTx: Long,
    val status: String,
    val schemaVersion: Int
) {
    val bytesTotal get() = bytesRx + bytesTx
}

/**
 * Storage seam for telemetry, bound to [RoomTelemetryRepository]. Each sample is committed with its
 * checkpoint in ONE transaction, so a crash leaves either both or neither and no bytes are counted
 * twice.
 */
interface TelemetryRepository {

    /** Checkpoint of the 30-second sampler; null when nothing was committed yet. */
    suspend fun primaryCheckpoint(): TelemetryCheckpoint?

    /** Stores [sample] and the [checkpoint] that follows it, atomically. */
    suspend fun commitPrimary(sample: UsageSample, checkpoint: TelemetryCheckpoint)

    /** Checkpoint of the 10-minute read; null before the first read. */
    suspend fun durableCheckpoint(): DurableCheckpoint?

    /** Stores [readings] (possibly none) and the [checkpoint] that follows them, atomically. */
    suspend fun commitDurable(readings: List<DurableUsageReading>, checkpoint: DurableCheckpoint)

    /** Stored 30-second samples, oldest first. */
    fun observeSamples(): Flow<List<UsageSample>>

    /** Stored 10-minute readings, oldest first. */
    fun observeDurableReadings(): Flow<List<DurableUsageReading>>
}
