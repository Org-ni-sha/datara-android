package com.capstone.datara.telemetry

import kotlin.math.abs

/**
 * State the 30-second sampler carries from one tick to the next. Committed together with the
 * sample it belongs to (see [com.capstone.datara.data.telemetry.TelemetryRepository.commitPrimary]),
 * so a restart can never count already-saved bytes again.
 *
 * Same fields as data-harvester's `CollectionCheckpoint`.
 */
data class TelemetryCheckpoint(
    /** TrafficStats mobile RX/TX of the last committed reading (-1 = no baseline yet). */
    val mobileRx: Long = -1L,
    val mobileTx: Long = -1L,
    /** SystemClock.elapsedRealtime() of the last committed tick; monotonic, resets on reboot. */
    val lastTickElapsed: Long = -1L,
    /** Wall-clock epoch millis of the last committed tick. */
    val lastTickWall: Long = -1L,
    /**
     * elapsedRealtime / wall clock of the reading that set [mobileRx]/[mobileTx]. Differs from the
     * last tick only after failed or held readings; the next interval starts here, so its bytes
     * and its duration cover the same span.
     */
    val counterElapsed: Long = -1L,
    val counterWall: Long = -1L,
    /**
     * Whether a cellular data network was up at the reading that set [mobileRx]/[mobileTx].
     * DATAra-only (see [PrimaryTick.compute]); true when unknown, which keeps the harvester's rules.
     */
    val mobileUpAtReading: Boolean = true
)

/**
 * The arithmetic of one 30-second tick, mirrored from data-harvester's `PrimaryTick` (v1.6.7):
 * reboot detection, the byte delta, the interval it spans, its measurement status, and the
 * checkpoint to commit together with the sample. No Android types, so the JVM unit tests can
 * drive it.
 *
 * One deliberate deviation, found on a real phone (2026-11-04) and not covered by 1.6.7: the
 * interface-returned rule documented on [compute].
 */
object PrimaryTick {

    /** Wall-clock vs. elapsedRealtime drift above this between two ticks = reboot. */
    const val BOOT_TIME_TOLERANCE_MS = 60_000L

    /**
     * Highest believable mobile throughput: 100 MiB/s, above peak Philippine 5G speeds. A tick
     * above this is a counter artifact, recorded as COUNTER_JUMP with 0 bytes and re-baselined.
     */
    const val MAX_PLAUSIBLE_BYTES_PER_SECOND = 100L * 1024 * 1024

    /**
     * TrafficStats' mobile counters only sum the mobile interfaces that are up, so they fall when
     * mobile data drops and return to the full cumulative value when it comes back. A fall without
     * a reboot is held for up to this long (0 bytes, COUNTER_RESET, old baseline kept); a fall
     * that lasts longer is accepted as a true reset and re-baselined at the lower value.
     */
    const val COUNTER_DROP_HOLD_MS = 10 * 60_000L

    /** Cumulative mobile counters as read from TrafficStats; -1 = UNSUPPORTED. */
    data class Counters(val rx: Long, val tx: Long) {
        val supported get() = rx >= 0 && tx >= 0
    }

    data class Result(
        val bytesRx: Long,
        val bytesTx: Long,
        val status: String,
        /** Wall-clock start of the span the bytes belong to (= nowWall when unknown). */
        val intervalStart: Long,
        /** Monotonic length of that span; -1 when unknown. */
        val elapsedMs: Long,
        val checkpoint: TelemetryCheckpoint
    ) {
        val bytesTotal get() = bytesRx + bytesTx
    }

    /**
     * @param mobileUp whether a cellular data network is up at this reading.
     *
     * Interface-returned rule (DATAra deviation from harvester 1.6.7): phones that tear mobile
     * data down on Wi-Fi report low mobile counters while it is down, and the interface's whole
     * lifetime total when it comes back. A baseline read while mobile data was down therefore
     * cannot be subtracted from a reading taken after it came back: on a test phone that counted
     * 1.2 GB in 29 s (about 41 MB/s, under the jump ceiling) where NetworkStatsManager measured
     * 23.5 MB. Such an interval is a COUNTER_RESET with 0 bytes and re-baselines; the service
     * ticks the moment mobile data comes up, so almost no real usage falls in it.
     */
    fun compute(
        cp: TelemetryCheckpoint,
        counters: Counters,
        nowElapsed: Long,
        nowWall: Long,
        mobileUp: Boolean = true
    ): Result {
        // Reboot: boot time (wall clock minus uptime) moves only on a reboot
        val hasPreviousTick = cp.lastTickElapsed >= 0 && cp.lastTickWall > 0
        val rebooted = hasPreviousTick && (
            nowElapsed < cp.lastTickElapsed ||
                abs((nowWall - nowElapsed) - (cp.lastTickWall - cp.lastTickElapsed)) > BOOT_TIME_TOLERANCE_MS
            )

        if (!counters.supported) {
            // Failed reading: no bytes, but the tick is recorded. The byte baseline AND its time
            // stay put, so the next good reading spans the whole gap; after a reboot the old
            // baseline is meaningless and the next good reading is a BASELINE
            val spanKnown = hasPreviousTick && !rebooted
            return Result(
                bytesRx = 0L, bytesTx = 0L,
                status = MeasurementStatus.UNSUPPORTED,
                intervalStart = if (spanKnown) cp.lastTickWall else nowWall,
                elapsedMs = if (spanKnown) nowElapsed - cp.lastTickElapsed else -1L,
                checkpoint = if (rebooted) {
                    TelemetryCheckpoint(lastTickElapsed = nowElapsed, lastTickWall = nowWall)
                } else {
                    cp.copy(lastTickElapsed = nowElapsed, lastTickWall = nowWall)
                }
            )
        }

        val hasBaseline = cp.mobileRx >= 0 && cp.mobileTx >= 0
        // The bytes span from the last SUCCESSFUL counter reading, not the last tick
        val hasCounterTime = cp.counterElapsed >= 0 && cp.counterWall > 0 && !rebooted
        val elapsedMs = if (hasCounterTime) nowElapsed - cp.counterElapsed else -1L

        // Counter fell without a reboot: most likely the mobile interface went down. Keep the
        // baseline and its time, like a failed reading, until it comes back or the hold ends
        val dropped = hasBaseline && !rebooted && (counters.rx < cp.mobileRx || counters.tx < cp.mobileTx)
        if (dropped && hasCounterTime && elapsedMs < COUNTER_DROP_HOLD_MS) {
            return Result(
                bytesRx = 0L, bytesTx = 0L,
                status = MeasurementStatus.COUNTER_RESET,
                intervalStart = cp.lastTickWall,
                elapsedMs = nowElapsed - cp.lastTickElapsed,
                checkpoint = cp.copy(lastTickElapsed = nowElapsed, lastTickWall = nowWall)
            )
        }

        // Baseline read while mobile data was down, reading taken after it came back (see above)
        val interfaceReturned = hasBaseline && !rebooted && mobileUp && !cp.mobileUpAtReading

        val deltaRx = counters.rx - cp.mobileRx
        val deltaTx = counters.tx - cp.mobileTx
        // Unknown span (no counter time): judge it as one ordinary tick, the strict choice
        val spanMs = (if (elapsedMs > 0) elapsedMs else MeasurementRules.COLLECTION_INTERVAL_MS).coerceAtLeast(1_000L)
        val implausible = (deltaRx + deltaTx) > MAX_PLAUSIBLE_BYTES_PER_SECOND * spanMs / 1_000L

        val counterStatus = when {
            !hasBaseline -> MeasurementStatus.BASELINE
            rebooted || dropped || interfaceReturned -> MeasurementStatus.COUNTER_RESET
            implausible -> MeasurementStatus.COUNTER_JUMP
            else -> MeasurementStatus.OK
        }
        val counted = counterStatus == MeasurementStatus.OK

        // A tick after the last good reading means a reading inside the span failed
        val readingsFailedInSpan = hasCounterTime && cp.lastTickElapsed != cp.counterElapsed

        return Result(
            bytesRx = if (counted) deltaRx else 0L,
            bytesTx = if (counted) deltaTx else 0L,
            status = MeasurementRules.primaryStatus(counterStatus, elapsedMs, readingsFailedInSpan),
            intervalStart = if (hasCounterTime) cp.counterWall else nowWall,
            elapsedMs = elapsedMs,
            checkpoint = TelemetryCheckpoint(
                mobileRx = counters.rx,
                mobileTx = counters.tx,
                lastTickElapsed = nowElapsed,
                lastTickWall = nowWall,
                counterElapsed = nowElapsed,
                counterWall = nowWall,
                mobileUpAtReading = mobileUp
            )
        )
    }
}
