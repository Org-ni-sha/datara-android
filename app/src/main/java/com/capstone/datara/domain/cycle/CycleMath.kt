package com.capstone.datara.domain.cycle

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.telemetry.MeasurementStatus
import java.math.BigInteger

/**
 * Usage measured inside one cycle window, derived from the 10-minute readings.
 *
 * @property usedBytes bytes in `[start_at, end)`; readings straddling either edge are split
 *   proportionally by time
 * @property estimatedBytes the part of [usedBytes] that came from such a split (the only estimate)
 * @property reachedAt end of the reading in which the running sum first reached the quota, or null
 * @property measuredThrough end of the latest counted reading (usage is known up to here), or null
 *   when no reading covers the window yet
 * @property unmeasuredMillis time inside `[start_at, measuredThrough)` that no reading covers:
 *   tracking was off, or the process died and the in-memory checkpoint was lost. Bytes used then
 *   are missing from [usedBytes].
 * @property lostReadings readings whose bytes are unknown: a zero-byte `COUNTER_RESET`,
 *   `COUNTER_JUMP` or `UNSUPPORTED`, or a `CLOCK_ERROR` span that cannot be placed in time
 */
data class CycleUsage(
    val usedBytes: Long,
    val estimatedBytes: Long,
    val reachedAt: Long?,
    val measuredThrough: Long?,
    val unmeasuredMillis: Long,
    val lostReadings: Int
) {
    /** True when some usage in the window may be missing, so the figures are a lower bound. */
    val isIncomplete: Boolean get() = unmeasuredMillis > 0 || lostReadings > 0

    companion object {
        val NONE = CycleUsage(0, 0, null, null, 0, 0)
    }
}

/**
 * Pure arithmetic for "used in cycle", per docs/erd-draft.md (`tracking_cycles`): no Android,
 * no storage, no clock. Units are bytes and epoch millis.
 */
object CycleMath {

    /** Statuses whose zero bytes mean "unknown", not "no usage" (see [MeasurementStatus]). */
    private val LOST_STATUSES = setOf(
        MeasurementStatus.COUNTER_RESET,
        MeasurementStatus.COUNTER_JUMP,
        MeasurementStatus.UNSUPPORTED
    )

    /**
     * Sums [readings] over the window `[startAt, endAt)`; [endAt] null means open-ended (an
     * active cycle: readings never extend past "now").
     *
     * Status rules:
     * - `OK`, `GAP`, `INTERRUPTED`, `BASELINE`: bytes count as recorded. A durable `GAP` is a
     *   long span with exact bytes (NetworkStatsManager totals are cumulative), so it counts too,
     *   split proportionally if it straddles an edge.
     * - `COUNTER_RESET`, `COUNTER_JUMP`, `UNSUPPORTED`: 0 bytes by definition; counted in
     *   [CycleUsage.lostReadings] because the real usage is unknown.
     * - `CLOCK_ERROR` (or any span whose end precedes its start): skipped, since it cannot be
     *   placed in the window; counted in [CycleUsage.lostReadings].
     */
    fun usage(readings: List<DurableUsageReading>, startAt: Long, endAt: Long?, quotaBytes: Long): CycleUsage {
        require(endAt == null || endAt >= startAt) { "Window ends before it starts" }
        val end = endAt ?: Long.MAX_VALUE
        var used = 0L
        var estimated = 0L
        var reachedAt: Long? = null
        var cursor = startAt
        var measuredThrough: Long? = null
        var unmeasured = 0L
        var lost = 0

        for (reading in readings.sortedWith(compareBy({ it.since }, { it.until }))) {
            val from = maxOf(reading.since, startAt)
            val to = minOf(reading.until, end)
            val pointInWindow = reading.since == reading.until && reading.since in startAt until end
            val overlaps = to > from || pointInWindow
            if (reading.status == MeasurementStatus.CLOCK_ERROR || reading.until < reading.since) {
                // Count it as lost only if it plausibly belongs to this window
                if (maxOf(reading.since, reading.until) >= startAt && minOf(reading.since, reading.until) < end) lost++
                continue
            }
            if (!overlaps) continue

            if (from > cursor) unmeasured += from - cursor
            cursor = maxOf(cursor, to)
            measuredThrough = maxOf(measuredThrough ?: to, to)
            if (reading.status in LOST_STATUSES) lost++

            val span = reading.until - reading.since
            val bytes = reading.bytesTotal
            val split = span > 0L && (from != reading.since || to != reading.until)
            val counted = if (split) proportion(bytes, to - from, span) else bytes
            if (split) estimated += counted

            used += counted
            if (reachedAt == null && used >= quotaBytes) reachedAt = to
        }
        return CycleUsage(used, estimated, reachedAt, measuredThrough, unmeasured, lost)
    }

    /** `bytes * part / whole`, floored, without overflowing Long. */
    internal fun proportion(bytes: Long, part: Long, whole: Long): Long =
        BigInteger.valueOf(bytes).multiply(BigInteger.valueOf(part)).divide(BigInteger.valueOf(whole)).toLong()

    /** Quota minus used; negative once usage goes past the quota. */
    fun remainingBytes(quotaBytes: Long, usedBytes: Long): Long = quotaBytes - usedBytes

    /** [remainingBytes] clamped at 0, for display. */
    fun displayRemainingBytes(quotaBytes: Long, usedBytes: Long): Long = remainingBytes(quotaBytes, usedBytes).coerceAtLeast(0)

    /** Used as a percentage of the quota, unclamped (110.0 means 10% over). */
    fun percentUsed(quotaBytes: Long, usedBytes: Long): Double {
        require(quotaBytes > 0) { "quota_bytes must be > 0" }
        return usedBytes * 100.0 / quotaBytes
    }
}
