package com.capstone.datara.data.telemetry

import com.capstone.datara.data.local.entity.DataUsage
import com.capstone.datara.telemetry.MeasurementStatus
import com.capstone.datara.telemetry.RadioLabels
import java.util.UUID
import kotlin.math.roundToInt

/**
 * How 30-second [UsageSample]s become 10-minute `data_usage` rows (docs/erd-draft.md,
 * "data_usage"). Pure, so the rules are unit-tested on the JVM.
 *
 * - **Windows** are aligned to 10-minute boundaries of epoch time (so also of PH local time). A
 *   sample belongs to the window in which its interval *ended*, i.e. when its bytes were read.
 * - **Bytes** are summed.
 * - **Status** is the worst one, by [STATUS_SEVERITY].
 * - **Network type** is the cellular label that carried the most bytes, otherwise the most common
 *   label (ties go to the most recent).
 * - **Signal** is the median of the known values (an even count averages the middle two), or null
 *   when there are none; the -999 sentinel is never stored.
 * - **Carrier** is the last one seen.
 */
object DataUsageRollup {

    const val WINDOW_MS = 10 * 60_000L

    /**
     * Statuses from worst to best. The ERD orders COUNTER_JUMP > … > OK; CLOCK_ERROR is not in its
     * list and is ranked worst here, because a window containing one has an untrusted time span.
     */
    val STATUS_SEVERITY = listOf(
        MeasurementStatus.CLOCK_ERROR,
        MeasurementStatus.COUNTER_JUMP,
        MeasurementStatus.COUNTER_RESET,
        MeasurementStatus.UNSUPPORTED,
        MeasurementStatus.GAP,
        MeasurementStatus.INTERRUPTED,
        MeasurementStatus.BASELINE,
        MeasurementStatus.OK
    )

    private val CELLULAR_LABELS = setOf("5G", "LTE", "3G", "2G", "CELLULAR")

    fun windowStart(time: Long): Long = Math.floorDiv(time, WINDOW_MS) * WINDOW_MS

    /**
     * The same id for the same device and window, so rolling a window up again (or re-uploading
     * it) upserts the existing row instead of colliding with the `(device_id, interval_start_at)`
     * unique key.
     */
    fun rowId(deviceId: String, windowStart: Long): String =
        UUID.nameUUIDFromBytes("data_usage:$deviceId:$windowStart".toByteArray()).toString()

    /** One row per window that has samples, in window order. [samples] are in commit order. */
    fun rollUp(samples: List<UsageSample>, userId: String, deviceId: String): List<DataUsage> =
        samples.groupBy { windowStart(it.intervalEnd) }
            .toSortedMap()
            .map { (start, window) -> window(start, window, userId, deviceId) }

    private fun window(start: Long, samples: List<UsageSample>, userId: String, deviceId: String) = DataUsage(
        dataUsageId = rowId(deviceId, start),
        userId = userId,
        deviceId = deviceId,
        intervalStartAt = start,
        intervalEndAt = start + WINDOW_MS,
        bytesRx = samples.sumOf { it.bytesRx },
        bytesTx = samples.sumOf { it.bytesTx },
        measurementStatus = worstStatus(samples.map { it.status }),
        networkType = networkType(samples),
        signalDbm = medianSignal(samples.map { it.signalDbm }),
        carrier = samples.last().carrier,
        schemaVersion = samples.last().schemaVersion
    )

    fun worstStatus(statuses: List<String>): String =
        statuses.minByOrNull { STATUS_SEVERITY.indexOf(it).let { i -> if (i < 0) 0 else i } } ?: MeasurementStatus.OK

    fun networkType(samples: List<UsageSample>): String? {
        val cellularBytes = samples.filter { it.networkType in CELLULAR_LABELS && it.bytesTotal > 0 }
            .groupBy { it.networkType }
            .mapValues { (_, s) -> s.sumOf { it.bytesTotal } }
        cellularBytes.maxByOrNull { it.value }?.let { return it.key }
        // Most common label; on a tie, the one seen last
        val counts = samples.groupingBy { it.networkType }.eachCount()
        return samples.asReversed().maxByOrNull { counts.getValue(it.networkType) }?.networkType
    }

    fun medianSignal(values: List<Int>): Int? {
        val known = values.filter { it != RadioLabels.SIGNAL_UNKNOWN }.sorted()
        if (known.isEmpty()) return null
        val mid = known.size / 2
        return if (known.size % 2 == 1) known[mid] else ((known[mid - 1] + known[mid]) / 2.0).roundToInt()
    }
}
