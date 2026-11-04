package com.capstone.datara.data.telemetry

import com.capstone.datara.data.local.dao.DataUsageDao
import com.capstone.datara.data.local.dao.TelemetryDao
import com.capstone.datara.data.local.device.DeviceRepository
import com.capstone.datara.data.session.CurrentUserSource
import com.capstone.datara.telemetry.DurableTick.DAY_MS
import com.capstone.datara.telemetry.TelemetryClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rolls closed 10-minute windows of `usage_samples` up into `data_usage` rows for the signed-in
 * user (rules in [DataUsageRollup]), then prunes old samples. Run after each durable read.
 *
 * A window is closed once the wall clock has passed its end: samples are stamped with the time
 * they are committed, so no later sample can end inside it. Each run continues after the device's
 * latest rolled-up window, so a window is rolled up once. While signed out nothing is rolled up
 * or pruned; the samples wait for the next signed-in run. Each window gets its own row even with
 * zero bytes: a measured zero differs from a window with no samples, which gets no row.
 */
@Singleton
class UsageRollup @Inject constructor(
    private val telemetryDao: TelemetryDao,
    private val dataUsageDao: DataUsageDao,
    private val devices: DeviceRepository,
    private val currentUser: CurrentUserSource,
    private val clock: TelemetryClock
) {
    private val lock = Mutex()

    /** @return the number of `data_usage` rows written */
    suspend fun run(): Int = lock.withLock {
        val userId = currentUser.userId() ?: return@withLock 0
        val now = clock.currentTimeMillis()
        val device = devices.ensureRegistered(userId, now)
        val closedUntil = DataUsageRollup.windowStart(now)

        // A device's first rollup starts at its registration, so samples another account on this
        // install already rolled up are never counted again under this one
        val from = dataUsageDao.latestIntervalStart(device.deviceId)?.plus(DataUsageRollup.WINDOW_MS)
            ?: telemetryDao.earliestSampleEnd()?.let {
                maxOf(DataUsageRollup.windowStart(it), DataUsageRollup.windowStart(device.createdAt))
            }
        var written = 0
        if (from != null) {
            // A day at a time, so a long catch-up never loads every sample at once
            var start: Long = from
            while (start < closedUntil) {
                val end = minOf(start + DAY_MS, closedUntil)
                val samples = telemetryDao.samplesEndingIn(start, end).map { it.toDomain() }
                val rows = DataUsageRollup.rollUp(samples, userId, device.deviceId)
                if (rows.isNotEmpty()) dataUsageDao.upsertAll(rows)
                written += rows.size
                start = end
            }
        }
        // Keep SAMPLE_RETENTION_MS of rolled-up samples for D6's short-term features
        telemetryDao.deleteSamplesEndingBefore(minOf(closedUntil, now - SAMPLE_RETENTION_MS))
        written
    }

    companion object {
        /** How long 30-second samples are kept after their window is rolled up. */
        const val SAMPLE_RETENTION_MS = 14 * DAY_MS
    }
}
