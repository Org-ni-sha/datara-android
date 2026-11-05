package com.capstone.datara.forecast

import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import com.capstone.datara.forecast.FeatureContract.N_FEATURES
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.sin

/**
 * One feature row in exactly the contract's order (`features.feature_vector` in datara-ml).
 * Values are computed in double precision and cast to float32 at the end, as in Python.
 *
 * Time features use [zone], the phone's own zone. Training used Philippine time (UTC+8), which is
 * the zone of every participant's phone.
 */
object FeatureExtractor {

    /**
     * Features for predicting the hour starting at [forecastHour], reading only earlier hours of
     * [history]. [network] overrides the last-hour network features (the projection's later steps).
     */
    fun featureVector(history: UsageHistory, forecastHour: Long, zone: ZoneId, network: NetworkFeatures? = null): FloatArray {
        require(forecastHour % HOUR_MS == 0L) { "forecastHour must be an hour boundary" }
        val local = Instant.ofEpochMilli(forecastHour).atZone(zone)
        val hour = local.hour
        val dow = local.dayOfWeek.value - 1 // Monday = 0
        val angle = 2.0 * PI * hour / 24.0
        val net = network ?: networkOfHour(history[forecastHour - HOUR_MS])
        val values = doubleArrayOf(
            sin(angle),
            cos(angle),
            dow.toDouble(),
            if (dow >= 5) 1.0 else 0.0,
            log1pOrNaN(history[forecastHour - HOUR_MS]?.mobileBytes),
            logAverage(history, forecastHour, 3),
            logAverage(history, forecastHour, 24),
            log1pOrNaN(history[forecastHour - 24 * HOUR_MS]?.mobileBytes),
            log1pOrNaN(history[forecastHour - 168 * HOUR_MS]?.mobileBytes),
            net.signalDbm,
            net.radioGen,
            net.cellularShare
        )
        check(values.size == N_FEATURES)
        return FloatArray(N_FEATURES) { values[it].toFloat() }
    }

    fun observedLast24h(history: UsageHistory, forecastHour: Long): Int =
        (1..24).count { history.containsKey(forecastHour - it * HOUR_MS) }

    /** The generation with the most readings; a tie goes to the higher one. NaN for none. */
    fun radioGen(counts: IntArray): Double {
        var best = Double.NaN
        var bestCount = 0
        counts.forEachIndexed { i, count ->
            if (count > 0 && count >= bestCount) {
                best = (i + 2).toDouble()
                bestCount = count
            }
        }
        return best
    }

    fun networkOfHour(stats: HourStats?): NetworkFeatures = if (stats == null) NetworkFeatures.MISSING else NetworkFeatures(
        stats.signalDbm,
        radioGen(stats.radio),
        if (stats.readings > 0) stats.cellularReadings.toDouble() / stats.readings else Double.NaN
    )

    /** The network over the 24 hours before [forecastHour], for projected hours after the first. */
    fun pooledNetwork(history: UsageHistory, forecastHour: Long): NetworkFeatures {
        val signals = mutableListOf<Double>()
        val radio = IntArray(4)
        var readings = 0
        var cellular = 0
        for (j in 1..24) {
            val s = history[forecastHour - j * HOUR_MS] ?: continue
            if (!s.signalDbm.isNaN()) signals += s.signalDbm
            s.radio.forEachIndexed { i, c -> radio[i] += c }
            readings += s.readings
            cellular += s.cellularReadings
        }
        return NetworkFeatures(
            HourlyUsage.median(signals),
            radioGen(radio),
            if (readings > 0) cellular.toDouble() / readings else Double.NaN
        )
    }

    private fun log1pOrNaN(bytes: Double?): Double = if (bytes == null) Double.NaN else ln1p(bytes)

    private fun logAverage(history: UsageHistory, forecastHour: Long, hours: Int): Double {
        val values = (1..hours).mapNotNull { history[forecastHour - it * HOUR_MS]?.mobileBytes }
        // Summed in the same order as Python's list (most recent first)
        return if (values.isEmpty()) Double.NaN else ln1p(values.sum() / values.size)
    }
}
