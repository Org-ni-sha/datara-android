package com.capstone.datara.telemetry

/** Cumulative mobile byte counters (TrafficStats). */
fun interface MobileCounterSource {
    /** Current cumulative mobile RX/TX; -1 values when the device does not support them. */
    fun read(): PrimaryTick.Counters
}

/** Durable mobile totals (NetworkStatsManager). */
interface DurableUsageSource {
    /** Whether Usage Access is granted; without it every query fails. */
    fun hasAccess(): Boolean

    /** Cumulative device-wide mobile usage over [anchor, until), or null when the query failed. */
    fun queryMobileCumulative(anchor: Long, until: Long): PrimaryTick.Counters?
}

/** Network inputs of the model, read through TelephonyManager and ConnectivityManager. */
interface RadioInfoSource {
    /** The default network now: WIFI, 5G, LTE, 3G, 2G, CELLULAR, ETHERNET, NONE or UNKNOWN. */
    fun activeNetworkLabel(): String

    /** The mobile radio's generation, even while Wi-Fi is the default; CELLULAR when unknown. */
    fun cellularRadioLabel(): String

    /** Cell signal strength in dBm, or [RadioLabels.SIGNAL_UNKNOWN]. */
    fun signalDbm(): Int

    /** Carrier of the default data SIM, or [RadioLabels.CARRIER_UNKNOWN]. */
    fun carrierName(): String

    /** Whether a cellular data network is up now, default or not (see PrimaryTick's mobileUp). */
    fun isMobileDataUp(): Boolean
}

/** Clocks of one tick, injectable so the collector can be tested. */
interface TelemetryClock {
    /** SystemClock.elapsedRealtime(): monotonic, includes deep sleep, resets on reboot. */
    fun elapsedRealtime(): Long

    fun currentTimeMillis(): Long

    fun utcOffsetMinutes(wall: Long): Int
}
