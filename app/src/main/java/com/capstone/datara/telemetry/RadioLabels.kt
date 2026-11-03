package com.capstone.datara.telemetry

import android.telephony.TelephonyManager

/**
 * Network and signal labels in the harvester's exact vocabulary (`DeviceInfoHelper`), so the
 * model's network features take the same values in training and in the app.
 */
object RadioLabels {

    /** Signal strength when it cannot be read; the harvester's sentinel. */
    const val SIGNAL_UNKNOWN = -999

    /** Carrier name when neither the SIM nor the network reports one. */
    const val CARRIER_UNKNOWN = "Unknown"

    /**
     * Label for a [TelephonyManager] `NETWORK_TYPE_*` value. NR (standalone 5G) is "5G"; 5G
     * non-standalone reports LTE here, as it did in the harvester.
     */
    @Suppress("DEPRECATION") // CDMA/EVDO/iDEN types are deprecated but still reported by old radios
    fun cellularRadioLabel(networkType: Int): String = when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
        else -> "CELLULAR"
    }

    /**
     * A cell's dBm as reported, except `CellInfo.UNAVAILABLE` (Int.MAX_VALUE), which becomes
     * [SIGNAL_UNKNOWN] instead of a 2-billion "dBm" feature value.
     */
    fun signalDbm(reported: Int?): Int =
        if (reported == null || reported == Int.MAX_VALUE) SIGNAL_UNKNOWN else reported

    /**
     * dBm from a GSM ASU (0–31), for Android 8–9 where SignalStrength has no per-cell dBm.
     * 99 means unknown.
     */
    fun gsmAsuToDbm(asu: Int): Int = if (asu in 0..31) -113 + 2 * asu else SIGNAL_UNKNOWN

    /** First non-blank of the SIM's and the network's operator names, trimmed. */
    fun carrierName(vararg candidates: String?): String =
        candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: CARRIER_UNKNOWN
}
