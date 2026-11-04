package com.capstone.datara.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.telemetry.DurableCheckpoint
import com.capstone.datara.telemetry.TelemetryCheckpoint

// Phone-only D4 tables, never synced. Each mirrors its D4 type field for field; the D4 types
// stay free of Room so the telemetry rules remain plain JVM code.

/** One 30-second [UsageSample]. */
@Entity(tableName = "usage_samples", indices = [Index(value = ["interval_end"])])
data class UsageSampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "interval_start") val intervalStart: Long,
    @ColumnInfo(name = "interval_end") val intervalEnd: Long,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "bytes_rx") val bytesRx: Long,
    @ColumnInfo(name = "bytes_tx") val bytesTx: Long,
    val status: String,
    @ColumnInfo(name = "network_type") val networkType: String,
    @ColumnInfo(name = "active_network_at_sample") val activeNetworkAtSample: String,
    @ColumnInfo(name = "signal_dbm") val signalDbm: Int,
    val carrier: String,
    @ColumnInfo(name = "utc_offset_minutes") val utcOffsetMinutes: Int,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int
) {
    fun toDomain() = UsageSample(
        id, intervalStart, intervalEnd, elapsedMs, bytesRx, bytesTx, status, networkType,
        activeNetworkAtSample, signalDbm, carrier, utcOffsetMinutes, schemaVersion
    )

    companion object {
        fun from(s: UsageSample) = UsageSampleEntity(
            s.id, s.intervalStart, s.intervalEnd, s.elapsedMs, s.bytesRx, s.bytesTx, s.status, s.networkType,
            s.activeNetworkAtSample, s.signalDbm, s.carrier, s.utcOffsetMinutes, s.schemaVersion
        )
    }
}

/** One 10-minute NetworkStatsManager [DurableUsageReading]. */
@Entity(tableName = "durable_readings", indices = [Index(value = ["until"])])
data class DurableReadingEntity(
    @PrimaryKey val id: String,
    val since: Long,
    val until: Long,
    @ColumnInfo(name = "bytes_rx") val bytesRx: Long,
    @ColumnInfo(name = "bytes_tx") val bytesTx: Long,
    val status: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int
) {
    fun toDomain() = DurableUsageReading(id, since, until, bytesRx, bytesTx, status, schemaVersion)

    companion object {
        fun from(r: DurableUsageReading) =
            DurableReadingEntity(r.id, r.since, r.until, r.bytesRx, r.bytesTx, r.status, r.schemaVersion)
    }
}

/** The single [TelemetryCheckpoint] row (`id` is always [SINGLE_ROW_ID]). */
@Entity(tableName = "telemetry_checkpoint")
data class TelemetryCheckpointEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "mobile_rx") val mobileRx: Long,
    @ColumnInfo(name = "mobile_tx") val mobileTx: Long,
    @ColumnInfo(name = "last_tick_elapsed") val lastTickElapsed: Long,
    @ColumnInfo(name = "last_tick_wall") val lastTickWall: Long,
    @ColumnInfo(name = "counter_elapsed") val counterElapsed: Long,
    @ColumnInfo(name = "counter_wall") val counterWall: Long,
    @ColumnInfo(name = "mobile_up_at_reading") val mobileUpAtReading: Boolean
) {
    fun toDomain() = TelemetryCheckpoint(
        mobileRx, mobileTx, lastTickElapsed, lastTickWall, counterElapsed, counterWall, mobileUpAtReading
    )

    companion object {
        fun from(c: TelemetryCheckpoint) = TelemetryCheckpointEntity(
            SINGLE_ROW_ID, c.mobileRx, c.mobileTx, c.lastTickElapsed, c.lastTickWall,
            c.counterElapsed, c.counterWall, c.mobileUpAtReading
        )
    }
}

/** The single [DurableCheckpoint] row (`id` is always [SINGLE_ROW_ID]). */
@Entity(tableName = "durable_checkpoint")
data class DurableCheckpointEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val anchor: Long,
    val rx: Long,
    val tx: Long,
    @ColumnInfo(name = "taken_at") val takenAt: Long
) {
    fun toDomain() = DurableCheckpoint(anchor, rx, tx, takenAt)

    companion object {
        fun from(c: DurableCheckpoint) = DurableCheckpointEntity(SINGLE_ROW_ID, c.anchor, c.rx, c.tx, c.takenAt)
    }
}

/** Primary key of the one-row checkpoint tables. */
const val SINGLE_ROW_ID = 0
