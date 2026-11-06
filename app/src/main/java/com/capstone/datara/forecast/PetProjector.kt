package com.capstone.datara.forecast

import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import com.capstone.datara.forecast.FeatureContract.PROJECTION_HORIZON_HOURS
import java.time.ZoneId
import kotlin.math.floor

/** Next-hour bytes and the PET of one projection; [reachAt] is null past the 30-day cap. */
data class Projection(val nextHourBytes: Double, val reachAt: Long?)

/**
 * The PET rule of ROADMAP D6 and Decision 2 (`features.project` in datara-ml): step hour by hour
 * from the remaining quota, subtracting each predicted hour, until it reaches zero.
 *
 * - Predicted hours become observed hours for the next steps' byte lags.
 * - The first step uses the real last-hour network; later steps use the 24-hour pooled network.
 * - The PET is interpolated linearly inside the hour that reaches zero.
 */
object PetProjector {

    /**
     * @param predictBytes next-hour bytes for one feature row (global, or global × local factor),
     *   with the forecast hour of that row
     */
    fun project(
        history: UsageHistory,
        forecastHour: Long,
        remainingBytes: Double,
        zone: ZoneId,
        horizonHours: Int = PROJECTION_HORIZON_HOURS,
        predictBytes: (features: FloatArray, hour: Long) -> Double
    ): Projection {
        val hist = HashMap(history)
        val pooled = FeatureExtractor.pooledNetwork(history, forecastHour)
        var cumulative = 0.0
        var first = Double.NaN
        for (k in 0 until horizonHours) {
            val t = forecastHour + k * HOUR_MS
            val x = FeatureExtractor.featureVector(hist, t, zone, if (k == 0) null else pooled)
            val b = maxOf(0.0, predictBytes(x, t))
            if (k == 0) {
                first = b
                if (remainingBytes <= 0) return Projection(first, forecastHour)
            }
            if (b > 0 && cumulative + b >= remainingBytes) {
                val fraction = (remainingBytes - cumulative) / b
                return Projection(first, t + floor(fraction * HOUR_MS).toLong())
            }
            cumulative += b
            hist[t] = HourStats(mobileBytes = b)
        }
        return Projection(first, null)
    }
}
