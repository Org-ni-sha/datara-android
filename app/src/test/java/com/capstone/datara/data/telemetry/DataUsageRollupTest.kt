package com.capstone.datara.data.telemetry

import com.capstone.datara.data.telemetry.DataUsageRollup.WINDOW_MS
import com.capstone.datara.telemetry.MeasurementStatus
import com.capstone.datara.telemetry.RadioLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataUsageRollupTest {

    /** 2026-11-04 08:00:00 UTC, a window boundary. */
    private val t0 = 1_793_779_200_000L

    private fun sample(
        end: Long,
        rx: Long = 0,
        tx: Long = 0,
        status: String = MeasurementStatus.OK,
        network: String = "LTE",
        signal: Int = -95,
        carrier: String = "Smart"
    ) = UsageSample(
        intervalStart = end - 30_000, intervalEnd = end, elapsedMs = 30_000, bytesRx = rx, bytesTx = tx,
        status = status, networkType = network, activeNetworkAtSample = network, signalDbm = signal,
        carrier = carrier, utcOffsetMinutes = 480, schemaVersion = 3
    )

    @Test
    fun `samples are grouped by the window their interval ended in`() {
        val rows = DataUsageRollup.rollUp(
            listOf(
                sample(t0 + 10_000, rx = 100),            // ends in window 0 (started in the previous one)
                sample(t0 + WINDOW_MS - 1, rx = 200, tx = 5),
                sample(t0 + WINDOW_MS, rx = 400)          // the boundary belongs to the next window
            ),
            userId = "u", deviceId = "d"
        )
        assertEquals(2, rows.size)
        assertEquals(t0, rows[0].intervalStartAt)
        assertEquals(t0 + WINDOW_MS, rows[0].intervalEndAt)
        assertEquals(300L, rows[0].bytesRx)
        assertEquals(5L, rows[0].bytesTx)
        assertEquals(400L, rows[1].bytesRx)
        assertEquals("u", rows[0].userId)
        assertNull(rows[0].syncedAt)
    }

    @Test
    fun `row ids are stable per device and window`() {
        assertEquals(DataUsageRollup.rowId("d", t0), DataUsageRollup.rowId("d", t0))
        assertNotEquals(DataUsageRollup.rowId("d", t0), DataUsageRollup.rowId("d", t0 + WINDOW_MS))
        assertNotEquals(DataUsageRollup.rowId("d", t0), DataUsageRollup.rowId("e", t0))
    }

    @Test
    fun `status is the worst in the window`() {
        assertEquals("OK", DataUsageRollup.worstStatus(listOf("OK", "OK")))
        assertEquals("BASELINE", DataUsageRollup.worstStatus(listOf("OK", "BASELINE")))
        assertEquals("GAP", DataUsageRollup.worstStatus(listOf("INTERRUPTED", "GAP", "BASELINE")))
        assertEquals("COUNTER_JUMP", DataUsageRollup.worstStatus(listOf("COUNTER_RESET", "COUNTER_JUMP", "UNSUPPORTED")))
        assertEquals("CLOCK_ERROR", DataUsageRollup.worstStatus(listOf("COUNTER_JUMP", "CLOCK_ERROR")))
    }

    @Test
    fun `network type is the cellular label with the most bytes`() {
        val samples = listOf(
            sample(t0 + 1, rx = 10, network = "5G"),
            sample(t0 + 2, rx = 500, network = "LTE"),
            sample(t0 + 3, rx = 0, network = "WIFI"),
            sample(t0 + 4, rx = 0, network = "WIFI"),
            sample(t0 + 5, rx = 0, network = "WIFI")
        )
        assertEquals("LTE", DataUsageRollup.networkType(samples))
    }

    @Test
    fun `without mobile bytes network type is the most common label, latest on a tie`() {
        assertEquals("WIFI", DataUsageRollup.networkType(listOf(
            sample(t0 + 1, network = "LTE"), sample(t0 + 2, network = "WIFI"), sample(t0 + 3, network = "WIFI")
        )))
        assertEquals("NONE", DataUsageRollup.networkType(listOf(
            sample(t0 + 1, network = "WIFI"), sample(t0 + 2, network = "NONE")
        )))
    }

    @Test
    fun `signal is the median of known values and never the sentinel`() {
        assertEquals(-90, DataUsageRollup.medianSignal(listOf(-100, -90, RadioLabels.SIGNAL_UNKNOWN, -80)))
        assertEquals(-95, DataUsageRollup.medianSignal(listOf(-100, -90)))
        assertEquals(-95, DataUsageRollup.medianSignal(listOf(-101, -90)))
        assertNull(DataUsageRollup.medianSignal(listOf(RadioLabels.SIGNAL_UNKNOWN, RadioLabels.SIGNAL_UNKNOWN)))
    }

    @Test
    fun `carrier is the last one seen`() {
        val row = DataUsageRollup.rollUp(
            listOf(sample(t0 + 1, carrier = "Smart"), sample(t0 + 2, carrier = "GLOBE")), "u", "d"
        ).single()
        assertEquals("GLOBE", row.carrier)
        assertEquals(3, row.schemaVersion)
    }
}
