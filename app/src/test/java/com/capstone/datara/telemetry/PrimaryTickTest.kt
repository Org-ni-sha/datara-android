package com.capstone.datara.telemetry

import com.capstone.datara.telemetry.MeasurementRules.GAP_THRESHOLD_MS
import com.capstone.datara.telemetry.PrimaryTick.Counters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interval math, failed readings, reboots and re-baselining of the 30-second sampler. */
class PrimaryTickTest {

    // Device booted at wall time boot; elapsedRealtime counts from there
    private val boot = 1_793_000_000_000L

    private fun tick(cp: TelemetryCheckpoint, rx: Long, tx: Long, elapsed: Long) =
        PrimaryTick.compute(cp, Counters(rx, tx), elapsed, boot + elapsed)

    private fun unsupported(cp: TelemetryCheckpoint, elapsed: Long) =
        PrimaryTick.compute(cp, Counters(-1, -1), elapsed, boot + elapsed)

    private val first = tick(TelemetryCheckpoint(), 1_000, 100, 100_000)

    @Test
    fun `first reading is a baseline with no bytes`() {
        assertEquals("BASELINE", first.status)
        assertEquals(0L, first.bytesTotal)
        assertEquals(-1L, first.elapsedMs)
        assertEquals(1_000L, first.checkpoint.mobileRx)
        assertEquals(100_000L, first.checkpoint.counterElapsed)
    }

    @Test
    fun `ordinary tick counts the delta over thirty seconds`() {
        val t = tick(first.checkpoint, 1_500, 150, 130_000)
        assertEquals("OK", t.status)
        assertEquals(500L, t.bytesRx)
        assertEquals(50L, t.bytesTx)
        assertEquals(30_000L, t.elapsedMs)
        assertEquals(boot + 100_000, t.intervalStart)
    }

    @Test
    fun `failed reading keeps the byte baseline and its time`() {
        val failed = unsupported(first.checkpoint, 130_000)
        assertEquals("UNSUPPORTED", failed.status)
        assertEquals(0L, failed.bytesTotal)
        assertEquals(1_000L, failed.checkpoint.mobileRx)
        assertEquals(100_000L, failed.checkpoint.counterElapsed)
        assertEquals(130_000L, failed.checkpoint.lastTickElapsed)
    }

    @Test
    fun `recovery after a failed reading spans from the last good reading and is interrupted`() {
        val failed = unsupported(first.checkpoint, 130_000)
        val recovered = tick(failed.checkpoint, 1_900, 100, 160_000)
        assertEquals(900L, recovered.bytesTotal)
        assertEquals(60_000L, recovered.elapsedMs)
        assertEquals(boot + 100_000, recovered.intervalStart)
        assertEquals("INTERRUPTED", recovered.status)
        assertEquals("OK", tick(recovered.checkpoint, 2_000, 100, 190_000).status)
    }

    @Test
    fun `long silence keeps real bytes but is a gap`() {
        val t = tick(first.checkpoint, 9_000, 100, 100_000 + GAP_THRESHOLD_MS + 1)
        assertEquals("GAP", t.status)
        assertEquals(8_000L, t.bytesTotal)
    }

    @Test
    fun `reboot re-baselines with no bytes instead of a negative delta`() {
        // elapsedRealtime restarted: the device rebooted between ticks
        val t = PrimaryTick.compute(first.checkpoint, Counters(50, 5), 20_000, boot + 3_600_000)
        assertEquals("COUNTER_RESET", t.status)
        assertEquals(0L, t.bytesTotal)
        assertEquals(-1L, t.elapsedMs)
        assertEquals(50L, t.checkpoint.mobileRx)
        // The next tick counts from the new baseline
        val next = PrimaryTick.compute(t.checkpoint, Counters(150, 5), 50_000, boot + 3_630_000)
        assertEquals("OK", next.status)
        assertEquals(100L, next.bytesTotal)
    }

    @Test
    fun `reboot is detected from boot time even when uptime grew past the old value`() {
        // Slept long enough after a reboot that elapsedRealtime is higher again
        val t = PrimaryTick.compute(first.checkpoint, Counters(5_000, 500), 200_000, boot + 86_400_000)
        assertEquals("COUNTER_RESET", t.status)
        assertEquals(0L, t.bytesTotal)
    }

    @Test
    fun `reboot while readings fail makes the next good reading a baseline`() {
        val failedAfterReboot = PrimaryTick.compute(first.checkpoint, Counters(-1, -1), 20_000, boot + 3_600_000)
        assertEquals(-1L, failedAfterReboot.checkpoint.mobileRx)
        val next = PrimaryTick.compute(failedAfterReboot.checkpoint, Counters(70, 7), 50_000, boot + 3_630_000)
        assertEquals("BASELINE", next.status)
        assertEquals(0L, next.bytesTotal)
    }

    @Test
    fun `counters going backwards without a reboot is a counter reset`() {
        val t = tick(first.checkpoint, 10, 1, 130_000)
        assertEquals("COUNTER_RESET", t.status)
        assertEquals(0L, t.bytesTotal)
    }

    @Test
    fun `mobile data dropping then coming back counts only real usage`() {
        val gb6 = 6L * 1024 * 1024 * 1024
        val base = tick(TelemetryCheckpoint(), gb6, 100, 100_000).checkpoint
        val down = tick(base, 0, 0, 130_000)
        assertEquals("COUNTER_RESET", down.status)
        assertEquals(0L, down.bytesTotal)
        assertEquals(gb6, down.checkpoint.mobileRx)
        assertEquals(100_000L, down.checkpoint.counterElapsed)

        val back = tick(down.checkpoint, gb6 + 2_000_000, 100, 160_000)
        assertEquals(2_000_000L, back.bytesTotal)
        assertEquals("INTERRUPTED", back.status)
        assertEquals(60_000L, back.elapsedMs)
        assertEquals("OK", tick(back.checkpoint, gb6 + 2_100_000, 100, 190_000).status)
    }

    @Test
    fun `counter staying low past the hold is accepted as a true reset`() {
        val base = tick(TelemetryCheckpoint(), 5_000_000, 0, 100_000).checkpoint
        val low = tick(base, 1_000, 0, 100_000 + PrimaryTick.COUNTER_DROP_HOLD_MS + 1)
        assertEquals("COUNTER_RESET", low.status)
        assertEquals(1_000L, low.checkpoint.mobileRx)
        assertEquals("OK", tick(low.checkpoint, 2_000, 0, 100_000 + PrimaryTick.COUNTER_DROP_HOLD_MS + 30_001).status)
    }

    // Interface-returned rule (DATAra deviation from harvester 1.6.7)

    private fun tick(cp: TelemetryCheckpoint, rx: Long, tx: Long, elapsed: Long, mobileUp: Boolean) =
        PrimaryTick.compute(cp, Counters(rx, tx), elapsed, boot + elapsed, mobileUp)

    @Test
    fun `baseline read with mobile data down then counter returning is re-baselined, not counted`() {
        // 1.2 GB in 29 s is about 41 MB/s: under the jump ceiling, so only this rule catches it
        val onWifi = tick(TelemetryCheckpoint(), 10_000, 1_000, 100_000, mobileUp = false)
        val idle = tick(onWifi.checkpoint, 10_000, 1_000, 130_000, mobileUp = false)
        assertEquals("OK", idle.status)
        assertEquals(0L, idle.bytesTotal)

        val back = tick(idle.checkpoint, 1_204_000_000, 800_000, 159_000, mobileUp = true)
        assertEquals("COUNTER_RESET", back.status)
        assertEquals(0L, back.bytesTotal)
        assertEquals(1_204_000_000L, back.checkpoint.mobileRx)
        assertTrue(back.checkpoint.mobileUpAtReading)

        val next = tick(back.checkpoint, 1_204_090_000, 807_114, 189_000, mobileUp = true)
        assertEquals("OK", next.status)
        assertEquals(97_114L, next.bytesTotal)
    }

    @Test
    fun `wifi stretch past the drop hold then mobile returning is re-baselined`() {
        val gb1 = 1L * 1024 * 1024 * 1024
        val base = tick(TelemetryCheckpoint(), gb1, 0, 100_000, mobileUp = true).checkpoint
        val down = tick(base, 0, 0, 130_000, mobileUp = false)
        assertEquals(gb1, down.checkpoint.mobileRx) // held
        val expired = tick(down.checkpoint, 0, 0, 100_000 + PrimaryTick.COUNTER_DROP_HOLD_MS + 1, mobileUp = false)
        assertEquals(0L, expired.checkpoint.mobileRx) // accepted as a reset, read while down
        assertFalse(expired.checkpoint.mobileUpAtReading)

        val back = tick(expired.checkpoint, gb1 + 5_000, 0, 100_000 + PrimaryTick.COUNTER_DROP_HOLD_MS + 20_001, mobileUp = true)
        assertEquals("COUNTER_RESET", back.status)
        assertEquals(0L, back.bytesTotal)
    }

    @Test
    fun `drop held from a reading with mobile data up still counts real usage when it returns`() {
        val gb6 = 6L * 1024 * 1024 * 1024
        val base = tick(TelemetryCheckpoint(), gb6, 100, 100_000, mobileUp = true).checkpoint
        val down = tick(base, 0, 0, 130_000, mobileUp = false)
        val back = tick(down.checkpoint, gb6 + 2_000_000, 100, 160_000, mobileUp = true)
        assertEquals(2_000_000L, back.bytesTotal)
        assertEquals("INTERRUPTED", back.status)
    }

    @Test
    fun `mobile data always up keeps the harvester's behaviour`() {
        val a = tick(TelemetryCheckpoint(), 1_000, 100, 100_000, mobileUp = true)
        val b = tick(a.checkpoint, 1_500, 150, 130_000, mobileUp = true)
        assertEquals("OK", b.status)
        assertEquals(550L, b.bytesTotal)
    }

    @Test
    fun `impossible jump is flagged, not counted, and re-baselined`() {
        val jump = tick(first.checkpoint, 1_000 + 6_333L * 1024 * 1024, 100, 107_000)
        assertEquals("COUNTER_JUMP", jump.status)
        assertEquals(0L, jump.bytesTotal)
        val next = tick(jump.checkpoint, 1_000 + 6_333L * 1024 * 1024 + 500, 100, 137_000)
        assertEquals("OK", next.status)
        assertEquals(500L, next.bytesTotal)
    }

    @Test
    fun `heavy but real usage under the ceiling stays OK`() {
        val t = tick(first.checkpoint, 1_000 + 900L * 1024 * 1024, 100, 130_000)
        assertEquals("OK", t.status)
        assertEquals(900L * 1024 * 1024, t.bytesTotal)
    }

    @Test
    fun `crash before commit counts the bytes exactly once`() {
        tick(first.checkpoint, 1_500, 100, 130_000) // computed, never committed
        val next = tick(first.checkpoint, 1_800, 100, 160_000)
        assertEquals(800L, next.bytesTotal)
        assertEquals(60_000L, next.elapsedMs)
    }
}
