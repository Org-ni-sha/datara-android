package com.capstone.datara.forecast

import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.forecast.FeatureContract.COUNTED_STATUSES
import com.capstone.datara.forecast.FeatureContract.NON_CELLULAR
import com.capstone.datara.forecast.FeatureContract.RADIO_GENERATIONS
import com.capstone.datara.forecast.FeatureContract.SIGNAL_UNKNOWN

/**
 * Builds the hourly history from the phone's 30-second `usage_samples`, with the same rules the
 * training data used (datara-ml `phantom_spikes.clean_hourly_mobile` and `features.hourly_network`).
 *
 * `data_usage` is not used: its 10-minute rows keep only the worst status and one network label,
 * so the per-reading counting rule and the hourly signal median could not be reproduced from it.
 * Samples are kept for 14 days (`UsageRollup.SAMPLE_RETENTION_MS`), more than the 8 days the
 * features read.
 */
object HourlyUsage {

    /** A sample belongs to the hour its interval ended in (the harvester's `timestamp`). */
    fun aggregate(samples: List<UsageSample>): UsageHistory =
        samples.groupBy { FeatureContract.hourStart(it.intervalEnd) }
            .mapValues { (_, hour) -> stats(hour) }

    private fun stats(hour: List<UsageSample>): HourStats {
        val radio = IntArray(4)
        hour.forEach { s -> RADIO_GENERATIONS[s.networkType]?.let { radio[it - 2]++ } }
        return HourStats(
            mobileBytes = hour.filter { counts(it) }.sumOf { it.bytesTotal }.toDouble(),
            readings = hour.size,
            cellularReadings = hour.count { it.networkType !in NON_CELLULAR },
            signalDbm = median(hour.map { it.signalDbm }.filter { isKnownSignal(it) }.map { it.toDouble() }),
            radio = radio
        )
    }

    fun counts(s: UsageSample): Boolean = s.status in COUNTED_STATUSES && s.networkType !in NON_CELLULAR

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
