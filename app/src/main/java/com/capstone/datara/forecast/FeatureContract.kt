package com.capstone.datara.forecast

/**
 * Feature contract v1 of the Global XGBoost next-hour model (datara-ml
 * `docs/feature-contract.md`, reference implementation `datara_ml/features.py`).
 *
 * The model reads [N_FEATURES] float32 values in exactly the order of [FEATURE_NAMES] and returns
 * log1p(mobile bytes in the next hour). Changing anything here needs a new [CONTRACT_VERSION], the
 * same change in datara-ml, and a regenerated parity fixture (`FeatureParityTest`).
 */
object FeatureContract {
    const val CONTRACT_VERSION = 1

    val FEATURE_NAMES = listOf(
        "hour_sin",                       // 0
        "hour_cos",                       // 1
        "day_of_week",                    // 2
        "is_weekend",                     // 3
        "log_bytes_last_1h",              // 4
        "log_avg_bytes_last_3h",          // 5
        "log_avg_bytes_last_24h",         // 6
        "log_bytes_same_hour_yesterday",  // 7
        "log_bytes_same_hour_last_week",  // 8
        "signal_dbm_last_hour",           // 9
        "radio_gen_last_hour",            // 10
        "cellular_share_last_hour"        // 11
    )
    val N_FEATURES = FEATURE_NAMES.size

    /** ONNX input/output names written by `export_onnx` in datara-ml. */
    const val INPUT_NAME = "features"
    const val OUTPUT_NAME = "variable"

    const val HOUR_MS = 3_600_000L

    /** The oldest hour any feature reads: same hour last week. */
    const val LOOKBACK_HOURS = 168

    /** Fewer observed hours than this in the last 24 means "still collecting": no forecast. */
    const val MIN_OBSERVED_LAST_24H = 6

    /** PET projection cap (30 days); past it the PET is "beyond 30 days". */
    const val PROJECTION_HORIZON_HOURS = 30 * 24

    /** Readings whose bytes count as usage (B1's `COUNTED_STATUSES`). */
    val COUNTED_STATUSES = setOf("OK", "LEGACY")

    /** Network labels that are not cellular (B1's `NON_CELLULAR`). */
    val NON_CELLULAR = setOf("WIFI", "NONE")

    /** Radio label to generation; any other label (CELLULAR, WIFI, ...) has none. */
    val RADIO_GENERATIONS = mapOf("2G" to 2, "3G" to 3, "LTE" to 4, "5G" to 5)

    const val SIGNAL_UNKNOWN = -999

    fun hourStart(time: Long): Long = Math.floorDiv(time, HOUR_MS) * HOUR_MS
}

/**
 * One observed hour (at least one 30-second reading).
 * @param radio readings per generation: 2G, 3G, LTE, 5G
 */
data class HourStats(
    val mobileBytes: Double,
    val readings: Int = 0,
    val cellularReadings: Int = 0,
    /** Median of the known signal readings; NaN when none. */
    val signalDbm: Double = Double.NaN,
    val radio: IntArray = IntArray(4)
) {
    override fun equals(other: Any?): Boolean = other is HourStats && mobileBytes == other.mobileBytes &&
        readings == other.readings && cellularReadings == other.cellularReadings &&
        signalDbm.equals(other.signalDbm) && radio.contentEquals(other.radio)

    override fun hashCode(): Int =
        listOf(mobileBytes, readings, cellularReadings, signalDbm, radio.contentHashCode()).hashCode()
}

/** Observed hours keyed by hour start (epoch ms). Unobserved hours are absent. */
typealias UsageHistory = Map<Long, HourStats>

data class NetworkFeatures(val signalDbm: Double, val radioGen: Double, val cellularShare: Double) {
    companion object {
        val MISSING = NetworkFeatures(Double.NaN, Double.NaN, Double.NaN)
    }
}
