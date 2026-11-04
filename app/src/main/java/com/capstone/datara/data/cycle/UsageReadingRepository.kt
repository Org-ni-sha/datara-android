package com.capstone.datara.data.cycle

import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.TelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The usage a cycle is measured from: the 10-minute NetworkStatsManager readings
 * (`durable_readings`), which ROADMAP and docs/erd-draft.md make the source of truth for
 * "used in cycle". The 30-second TrafficStats samples are not used here.
 */
interface UsageReadingRepository {

    /** Readings that end after [fromMillis], oldest first, re-emitted whenever a read is stored. */
    fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>>
}

/**
 * Room `durable_readings` through [TelemetryDao.observeReadingsSince], which the app binds. Unlike
 * [TelemetryRepository.observeDurableReadings] (the most recent day only), it returns every
 * reading since the cycle started, however long ago.
 */
@Singleton
class RoomUsageReadingRepository @Inject constructor(
    private val dao: TelemetryDao
) : UsageReadingRepository {

    override fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>> =
        dao.observeReadingsSince(fromMillis).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()
}

/**
 * Reads through the telemetry seam, for JVM tests against [com.capstone.datara.data.telemetry.InMemoryTelemetryRepository].
 * Same filter as the DAO query: readings that end after [fromMillis].
 */
class TelemetryUsageReadingRepository @Inject constructor(
    private val telemetry: TelemetryRepository
) : UsageReadingRepository {

    override fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>> =
        telemetry.observeDurableReadings()
            .map { readings -> readings.filter { it.until > fromMillis }.sortedBy { it.since } }
            .distinctUntilChanged()
}
