package com.capstone.datara.data.cycle

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

    /**
     * Readings that end at or after [fromMillis], oldest first, re-emitted whenever a read is
     * stored. A Room DAO can serve this as
     * `select * from durable_readings where max(since, until) >= :from order by since`.
     */
    fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>>
}

/** Reads through the D4 telemetry seam, so it follows [TelemetryRepository]'s binding (in memory until D2). */
@Singleton
class TelemetryUsageReadingRepository @Inject constructor(
    private val telemetry: TelemetryRepository
) : UsageReadingRepository {

    override fun observeReadingsFrom(fromMillis: Long): Flow<List<DurableUsageReading>> =
        telemetry.observeDurableReadings()
            .map { readings -> readings.filter { maxOf(it.since, it.until) >= fromMillis }.sortedBy { it.since } }
            .distinctUntilChanged()
}
