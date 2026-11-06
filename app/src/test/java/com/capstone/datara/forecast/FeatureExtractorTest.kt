package com.capstone.datara.forecast

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.forecast.FeatureContract.HOUR_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.math.ln1p

/** The contract rules with hand-checked values (the same cases as datara-ml `tests/test_features.py`). */
class FeatureExtractorTest {

    private val manila = ZoneId.of("Asia/Manila")
    private val monMidnight = 1_794_153_600_000L // 2026-11-09 00:00 Asia/Manila, a Monday
    private val mb = 1_000_000.0

    private fun idx(name: String) = FeatureContract.FEATURE_NAMES.indexOf(name)
    private fun stats(mbUsed: Double, readings: Int = 120, cellular: Int = 120, signal: Double = -90.0,
                      radio: IntArray = intArrayOf(0, 0, 120, 0)) = HourStats(mbUsed * mb, readings, cellular, signal, radio)

    private fun sample(end: Long, bytes: Long, net: String = "LTE", status: String = "OK", signal: Int = -90) = UsageSample(
        intervalStart = end - 30_000, intervalEnd = end, elapsedMs = 30_000, bytesRx = bytes, bytesTx = 0,
        status = status, networkType = net, activeNetworkAtSample = net, signalDbm = signal, carrier = "Globe",
        utcOffsetMinutes = 480, schemaVersion = 4
    )

    @Test
    fun `monday midnight and saturday evening in Manila`() {
        val x = FeatureExtractor.featureVector(emptyMap(), monMidnight, manila)
        assertEquals(0f, x[idx("hour_sin")], 1e-6f)
        assertEquals(1f, x[idx("hour_cos")], 1e-6f)
        assertEquals(0f, x[idx("day_of_week")])
        assertEquals(0f, x[idx("is_weekend")])
        val sat = FeatureExtractor.featureVector(emptyMap(), monMidnight + (5 * 24 + 18) * HOUR_MS, manila)
        assertEquals(5f, sat[idx("day_of_week")])
        assertEquals(1f, sat[idx("is_weekend")])
        assertEquals(-1f, sat[idx("hour_sin")], 1e-6f)
    }

    @Test
    fun `lags and averages read only observed earlier hours`() {
        val t = monMidnight + 200 * HOUR_MS
        val h = (1..169).associate { t - it * HOUR_MS to stats(it.toDouble()) }.toMutableMap()
        h.remove(t - 2 * HOUR_MS)
        h[t] = stats(9999.0) // the forecast hour itself is never read
        val x = FeatureExtractor.featureVector(h, t, manila)
        assertEquals(ln1p(1 * mb).toFloat(), x[idx("log_bytes_last_1h")], 1e-5f)
        assertEquals(ln1p(2 * mb).toFloat(), x[idx("log_avg_bytes_last_3h")], 1e-5f) // (1 + 3) / 2
        assertEquals(ln1p(24 * mb).toFloat(), x[idx("log_bytes_same_hour_yesterday")], 1e-5f)
        assertEquals(ln1p(168 * mb).toFloat(), x[idx("log_bytes_same_hour_last_week")], 1e-5f)
    }

    @Test
    fun `missing history is NaN, a measured zero is not`() {
        val t = monMidnight + 10 * HOUR_MS
        val empty = FeatureExtractor.featureVector(emptyMap(), t, manila)
        (4 until FeatureContract.N_FEATURES).forEach { assertTrue(FeatureContract.FEATURE_NAMES[it], empty[it].isNaN()) }
        val zero = FeatureExtractor.featureVector(mapOf(t - HOUR_MS to stats(0.0)), t, manila)
        assertEquals(0f, zero[idx("log_bytes_last_1h")])
    }

    @Test
    fun `network features and radio ties`() {
        val t = monMidnight + 10 * HOUR_MS
        val x = FeatureExtractor.featureVector(mapOf(t - HOUR_MS to stats(1.0, 100, 25, -97.5, intArrayOf(0, 5, 20, 0))), t, manila)
        assertEquals(-97.5f, x[idx("signal_dbm_last_hour")])
        assertEquals(4f, x[idx("radio_gen_last_hour")])
        assertEquals(0.25f, x[idx("cellular_share_last_hour")], 1e-6f)
        assertEquals(4.0, FeatureExtractor.radioGen(intArrayOf(0, 3, 3, 0)), 0.0)
        assertEquals(5.0, FeatureExtractor.radioGen(intArrayOf(0, 0, 3, 3)), 0.0)
        assertTrue(FeatureExtractor.radioGen(IntArray(4)).isNaN())
    }

    @Test
    fun `pooled network over 24 hours`() {
        val t = monMidnight + 30 * HOUR_MS
        val h = mapOf(
            t - 1 * HOUR_MS to stats(1.0, 100, 0, Double.NaN, IntArray(4)),
            t - 2 * HOUR_MS to stats(1.0, 100, 100, -80.0, intArrayOf(0, 60, 40, 0)),
            t - 3 * HOUR_MS to stats(1.0, 100, 50, -100.0, intArrayOf(0, 0, 50, 0)),
            t - 25 * HOUR_MS to stats(1.0, 100, 100, -50.0, intArrayOf(100, 0, 0, 0))
        )
        val n = FeatureExtractor.pooledNetwork(h, t)
        assertEquals(-90.0, n.signalDbm, 0.0)
        assertEquals(4.0, n.radioGen, 0.0)
        assertEquals(0.5, n.cellularShare, 1e-12)
    }

    private fun reading(since: Long, until: Long, bytes: Long, status: String = "OK") =
        DurableUsageReading(since = since, until = until, bytesRx = bytes, bytesTx = 0, status = status, schemaVersion = 4)

    @Test
    fun `hourly bytes come from NSM readings, network stats from the samples`() {
        val h0 = monMidnight
        val m = 60_000L
        val history = HourlyUsage.aggregate(
            readings = listOf(
                reading(h0, h0 + 30 * m, 6_000_000),
                reading(h0 + 30 * m, h0 + 90 * m, 4_000_000, "GAP"), // straddles the hour: half each
                reading(h0 + 90 * m, h0 + 120 * m, 0),
                reading(h0 + 120 * m, h0 + 300 * m, 9_000_000, "GAP") // 3 h: its hours are unobserved
            ),
            samples = listOf(
                sample(h0 + 30_000, 1, signal = -91),
                sample(h0 + 60_000, 1, signal = -999),
                sample(h0 + 90_000, 0, net = "WIFI", signal = -85),
                sample(h0 + 120_000, 1, net = "5G", signal = 0),
                sample(h0 + 150_000, 1, net = "CELLULAR", signal = -80)
            )
        )
        assertEquals(setOf(h0, h0 + HOUR_MS), history.keys)
        val first = history.getValue(h0)
        assertEquals(8_000_000.0, first.mobileBytes, 1e-6)
        assertEquals(5, first.readings)
        assertEquals(4, first.cellularReadings)
        assertEquals(-85.0, first.signalDbm, 0.0)
        assertEquals(listOf(0, 0, 2, 1), first.radio.toList())
        val second = history.getValue(h0 + HOUR_MS) // measured by NSM, no 30-second samples
        assertEquals(2_000_000.0, second.mobileBytes, 1e-6)
        assertEquals(0, second.readings)
        assertTrue(FeatureExtractor.networkOfHour(second).cellularShare.isNaN())
    }

    @Test
    fun `hours with a reset, an overlap or a hole are unobserved`() {
        val m = 60_000L
        val h = HourlyUsage.hourlyFromWindows(listOf(
            HourlyUsage.Window(monMidnight, monMidnight + 60 * m, 0.0, "COUNTER_RESET"),
            HourlyUsage.Window(monMidnight + 60 * m, monMidnight + 100 * m, 1.0, "OK"),
            HourlyUsage.Window(monMidnight + 90 * m, monMidnight + 120 * m, 1.0, "OK"),
            HourlyUsage.Window(monMidnight + 125 * m, monMidnight + 180 * m, 1.0, "OK")
        ))
        assertEquals(3, h.size)
        assertTrue(h.values.all { it.isNaN() })
    }

    @Test
    fun `projection interpolates, caps, and feeds predictions back`() {
        val t = monMidnight + 48 * HOUR_MS
        val h = (1..29).associate { t - it * HOUR_MS to stats(10.0) }
        val tenMb = ln1p(10 * mb)
        val p = PetProjector.project(h, t, 25 * mb, manila) { _, _ -> tenMb }
        assertEquals(t + 2.5 * HOUR_MS, p.reachAt!!.toDouble(), 2.0)
        assertNull(PetProjector.project(h, t, 10 * mb * 721, manila) { _, _ -> tenMb }.reachAt)
        assertEquals(t, PetProjector.project(h, t, 0.0, manila) { _, _ -> tenMb }.reachAt)
        val seen = mutableListOf<Float>()
        PetProjector.project(h, t, 5 * mb, manila) { x, _ -> seen += x[idx("log_bytes_last_1h")]; ln1p(2 * mb) }
        assertEquals(ln1p(10 * mb).toFloat(), seen[0], 1e-4f)
        assertEquals(ln1p(2 * mb).toFloat(), seen[1], 1e-4f)
    }
}
