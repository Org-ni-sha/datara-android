package com.capstone.datara.telemetry

/**
 * Last cumulative NetworkStatsManager reading, carried from one 10-minute read to the next.
 * Same role as data-harvester's per-transport `AppUsageBaseline` marker row.
 */
data class DurableCheckpoint(
    /** UTC midnight the cumulative totals are counted from. */
    val anchor: Long,
    val rx: Long,
    val tx: Long,
    /** Wall-clock time of the reading. */
    val takenAt: Long
)

/**
 * The arithmetic of one 10-minute NetworkStatsManager read, mirrored from data-harvester's
 * `collectTransport` (device-wide mobile totals here, not per app).
 *
 * NetworkStatsManager keeps history in buckets about 2 hours long, so it cannot answer "how much
 * in the last 10 minutes" directly. Each read therefore asks for CUMULATIVE usage since a UTC
 * midnight anchor (a bucket boundary), with an end time a day in the future so the bucket in
 * progress counts in full, and stores the difference from the previous reading.
 */
object DurableTick {

    const val DAY_MS = 86_400_000L

    /** A measured span between two readings. */
    data class Span(
        val since: Long,
        val until: Long,
        val bytesRx: Long,
        val bytesTx: Long,
        val status: String
    ) {
        val bytesTotal get() = bytesRx + bytesTx
    }

    data class Result(val spans: List<Span>, val checkpoint: DurableCheckpoint)

    /** UTC midnight at or before [time]: a multiple of every NetworkStats bucket length. */
    fun utcMidnight(time: Long): Long = time - time.mod(DAY_MS)

    /**
     * @param query cumulative mobile usage over [anchor, until), or null when the query failed
     * @return the spans to store and the new checkpoint, or null when a query failed (the caller
     *   keeps the previous checkpoint and retries at the next read)
     */
    fun compute(
        previous: DurableCheckpoint?,
        now: Long,
        query: (anchor: Long, until: Long) -> PrimaryTick.Counters?
    ): Result? {
        val anchor = utcMidnight(now)
        val spans = mutableListOf<Span>()

        // New UTC day: close everything since the last reading up to this midnight. That range
        // lies wholly in the past and on bucket boundaries, so it is exact
        val todayBaseline: PrimaryTick.Counters
        val todaySince: Long
        if (previous != null && previous.anchor != anchor) {
            val closing = query(previous.anchor, anchor) ?: return null
            spans += span(closing, PrimaryTick.Counters(previous.rx, previous.tx), previous.takenAt, anchor)
            // Today's cumulative starts from zero at midnight
            todayBaseline = PrimaryTick.Counters(0L, 0L)
            todaySince = anchor
        } else {
            todayBaseline = PrimaryTick.Counters(previous?.rx ?: 0L, previous?.tx ?: 0L)
            todaySince = previous?.takenAt ?: now
        }

        val current = query(anchor, now + DAY_MS) ?: return null

        // First reading ever: store the baseline only
        if (previous != null) {
            spans += span(current, todayBaseline, todaySince, now)
        }

        return Result(spans, DurableCheckpoint(anchor, current.rx, current.tx, now))
    }

    /**
     * A reading below its baseline (stats wiped) is a zero-byte COUNTER_RESET span; long or
     * backwards spans are flagged by [MeasurementRules.appStatus]. Unlike the harvester's
     * per-app rows, zero-usage spans are kept: for a device total, "no usage" is a measurement.
     */
    private fun span(current: PrimaryTick.Counters, baseline: PrimaryTick.Counters, since: Long, until: Long): Span {
        val dRx = current.rx - baseline.rx
        val dTx = current.tx - baseline.tx
        val reset = dRx < 0 || dTx < 0
        return Span(
            since = since,
            until = until,
            bytesRx = if (reset) 0L else dRx,
            bytesTx = if (reset) 0L else dTx,
            status = MeasurementRules.appStatus(since, until, counterReset = reset)
        )
    }
}
