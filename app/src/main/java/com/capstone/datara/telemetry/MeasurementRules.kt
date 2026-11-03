package com.capstone.datara.telemetry

/**
 * Labelling and quality rules for measured intervals, mirrored from data-harvester's
 * `MeasurementRules` (schema version 3) so that an interval recorded here means the same thing
 * as one in the training data. Change a rule only together with the harvester; B2's features are
 * computed from rows labelled by these rules.
 *
 * Kept free of Android types so the JVM unit tests can check them directly.
 */
object MeasurementRules {

    /** The harvester's schema_version these rules match; bump only together with it. */
    const val SCHEMA_VERSION = 3

    /** 30 s TrafficStats sampling (ROADMAP Decision 1). */
    const val COLLECTION_INTERVAL_MS = 30_000L

    /** 10 min NetworkStatsManager read, inside the foreground service (ROADMAP Decision 1). */
    const val APP_COLLECTION_INTERVAL_MS = 600_000L

    /** A primary interval longer than this (Doze, service killed, stopped) is a GAP. */
    const val GAP_THRESHOLD_MS = 2 * COLLECTION_INTERVAL_MS + 15_000L

    /** A 10-minute interval longer than this is a GAP, not an ordinary sample. */
    const val APP_GAP_THRESHOLD_MS = 2 * APP_COLLECTION_INTERVAL_MS + 60_000L

    private val CELLULAR_LABELS = setOf("5G", "LTE", "3G", "2G", "CELLULAR")

    fun isCellular(label: String) = label in CELLULAR_LABELS

    /**
     * Network label of a primary interval. The primary counter measures MOBILE bytes only, so any
     * interval with mobile bytes (however few) or with a mobile default network gets the cellular
     * label. A non-cellular label (WIFI, NONE, ...) is kept only when the interval moved no mobile
     * bytes at all, so dropping WIFI rows never drops mobile usage.
     */
    fun primaryNetworkLabel(
        intervalNetwork: String,
        cellularSeen: Boolean,
        bytesTotal: Long,
        cellularRadio: () -> String
    ): String =
        if (!isCellular(intervalNetwork) && (bytesTotal > 0 || cellularSeen)) cellularRadio()
        else intervalNetwork

    /**
     * Status of a primary interval.
     * @param counterStatus the counter status from [PrimaryTick] (OK unless the read itself failed)
     * @param elapsedMs monotonic span since the last SUCCESSFUL counter reading; -1 if unknown
     * @param readingsFailedInSpan true when a tick inside the span could not read the counter
     */
    fun primaryStatus(counterStatus: String, elapsedMs: Long, readingsFailedInSpan: Boolean): String = when {
        counterStatus != MeasurementStatus.OK -> counterStatus
        elapsedMs < 0 || elapsedMs > GAP_THRESHOLD_MS -> MeasurementStatus.GAP
        readingsFailedInSpan -> MeasurementStatus.INTERRUPTED
        else -> MeasurementStatus.OK
    }

    /** Status of a 10-minute interval covering the wall-clock span [since, until]. */
    fun appStatus(since: Long, until: Long, counterReset: Boolean): String = when {
        counterReset -> MeasurementStatus.COUNTER_RESET
        until < since -> MeasurementStatus.CLOCK_ERROR
        until - since > APP_GAP_THRESHOLD_MS -> MeasurementStatus.GAP
        else -> MeasurementStatus.OK
    }
}

/**
 * The harvester's measurement_status values. Stored as text so Room and Supabase can keep the
 * exact strings the training data uses.
 */
object MeasurementStatus {
    /** Ordinary interval of about the expected length. */
    const val OK = "OK"

    /** First reading; no delta yet. */
    const val BASELINE = "BASELINE"

    /** Counters went backwards (reboot, mobile interface down, stats wiped); delta forced to 0. */
    const val COUNTER_RESET = "COUNTER_RESET"

    /** Implausibly large delta (a counter artifact, not usage); recorded with 0 bytes. */
    const val COUNTER_JUMP = "COUNTER_JUMP"

    /** The counter could not be read; 0 bytes is not "no usage". */
    const val UNSUPPORTED = "UNSUPPORTED"

    /** Real bytes, but over a much longer (or unknown) span than normal. */
    const val GAP = "GAP"

    /** Real bytes; one or more readings inside the span failed. */
    const val INTERRUPTED = "INTERRUPTED"

    /** The wall clock ran backwards, so the span cannot be trusted. */
    const val CLOCK_ERROR = "CLOCK_ERROR"
}
