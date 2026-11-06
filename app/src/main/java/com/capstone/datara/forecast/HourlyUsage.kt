package com.capstone.datara.forecast

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.forecast.FeatureContract.COVERAGE_TOLERANCE_MS
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import com.capstone.datara.forecast.FeatureContract.MAX_WINDOW_MS
import com.capstone.datara.forecast.FeatureContract.NON_CELLULAR
import com.capstone.datara.forecast.FeatureContract.RADIO_GENERATIONS
import com.capstone.datara.forecast.FeatureContract.SIGNAL_UNKNOWN
import com.capstone.datara.forecast.FeatureContract.USABLE_WINDOW_STATUSES

/**
 * The hourly history, with the same rules as the training data (feature contract v2):
 *
 * - **Mobile bytes** come from the 10-minute NetworkStatsManager `durable_readings`
 *   (datara-ml `nsm_hourly.hourly_from_windows`): each usable reading's bytes are spread evenly
 *   over its span and summed per clock hour. An hour is observed only when every reading touching
 *   it is usable (OK or GAP, and at most 2 hours or 0 bytes), they do not overlap, and they cover the hour
 *   within a minute. NSM keeps counting while the app sleeps; the 30-second TrafficStats samples
 *   miss that time (datara-ml `docs/measurement-gaps.md`).
 * - **Network statistics** (readings, cellular share, signal, radio generation) come from the
 *   30-second `usage_samples` whose interval ended in the hour (`features.hourly_network`). An
 *   observed hour with no samples has them missing.
 */
object HourlyUsage {

    /** One NSM window for the hourly rule. */
    data class Window(val start: Long, val end: Long, val mobileBytes: Double, val status: String) {
        /** At most 2 hours, or any length with 0 bytes: a zero is exact however long (contract v3). */
        val usable: Boolean get() = status in USABLE_WINDOW_STATUSES && end > start &&
            (end - start <= MAX_WINDOW_MS || mobileBytes == 0.0)
    }

    fun window(r: DurableUsageReading) = Window(r.since, r.until, r.bytesTotal.toDouble(), r.status)

    /** Hour start to mobile bytes for every hour a window touches; NaN when unobserved. */
    fun hourlyFromWindows(windows: List<Window>): Map<Long, Double> {
        val byHour = HashMap<Long, MutableList<Window>>()
        for (w in windows) {
            if (w.end <= w.start) {
                // Bytes with no span: the hour can't be trusted
                if (w.mobileBytes > 0) byHour.getOrPut(FeatureContract.hourStart(w.start)) { mutableListOf() } += w
                continue
            }
            var h = FeatureContract.hourStart(w.start)
            while (h < w.end) {
                byHour.getOrPut(h) { mutableListOf() } += w
                h += HOUR_MS
            }
        }
        return byHour.mapValues { (hour, ws) -> hourBytes(hour, ws) }
    }

    private fun hourBytes(hour: Long, ws: List<Window>): Double {
        if (ws.any { !it.usable }) return Double.NaN
        val endOfHour = hour + HOUR_MS
        var covered = 0L
        var lastEnd = hour
        var total = 0.0
        for (w in ws.sortedWith(compareBy({ it.start }, { it.end }))) {
            val s = maxOf(w.start, hour)
            val e = minOf(w.end, endOfHour)
            if (s < lastEnd) return Double.NaN // overlapping windows
            covered += e - s
            lastEnd = e
            // Same operation order as Python: bytes * share / span
            total += w.mobileBytes * (e - s) / (w.end - w.start)
        }
        return if (HOUR_MS - covered > COVERAGE_TOLERANCE_MS) Double.NaN else total
    }

    /** The observed hours: NSM bytes, with network statistics from the 30-second samples. */
    fun aggregate(readings: List<DurableUsageReading>, samples: List<UsageSample>): UsageHistory {
        val samplesByHour = samples.groupBy { FeatureContract.hourStart(it.intervalEnd) }
        return hourlyFromWindows(readings.map(::window))
            .filterValues { !it.isNaN() }
            .mapValues { (hour, bytes) -> stats(bytes, samplesByHour[hour].orEmpty()) }
    }

    private fun stats(mobileBytes: Double, hour: List<UsageSample>): HourStats {
        val radio = IntArray(4)
        hour.forEach { s -> RADIO_GENERATIONS[s.networkType]?.let { radio[it - 2]++ } }
        return HourStats(
            mobileBytes = mobileBytes,
            readings = hour.size,
            cellularReadings = hour.count { it.networkType !in NON_CELLULAR },
            signalDbm = median(hour.map { it.signalDbm }.filter { isKnownSignal(it) }.map { it.toDouble() }),
            radio = radio
        )
    }

    /** Not the -999 sentinel and negative (0 or above is a bad read). */
    fun isKnownSignal(dbm: Int): Boolean = dbm != SIGNAL_UNKNOWN && dbm < 0

    /** An even count averages the middle two; NaN for none. */
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
    }
}
