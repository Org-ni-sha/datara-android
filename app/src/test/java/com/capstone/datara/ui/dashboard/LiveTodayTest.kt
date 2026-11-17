package com.capstone.datara.ui.dashboard

import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.data.telemetry.UsageSample
import com.capstone.datara.telemetry.MeasurementStatus
import com.capstone.datara.ui.history.HistoryAggregation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LiveTodayTest {
    private val zone = ZoneOffset.ofHours(8)
    private val min = 60_000L
    private val day = LocalDate.of(2026, 11, 18).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun reading(fromMin: Long, toMin: Long, bytes: Long, status: String = MeasurementStatus.OK) =
        DurableUsageReading(since = day + fromMin * min, until = day + toMin * min, bytesRx = bytes, bytesTx = 0,
            status = status, schemaVersion = 3)

    private fun sample(endSec: Long, bytes: Long, status: String = MeasurementStatus.OK) = UsageSample(
        intervalStart = day + (endSec - 30) * 1_000, intervalEnd = day + endSec * 1_000, elapsedMs = 30_000,
        bytesRx = bytes, bytesTx = 0, status = status, networkType = "LTE", activeNetworkAtSample = "LTE",
        signalDbm = -90, carrier = "GLOBE", utcOffsetMinutes = 480, schemaVersion = 3)

    @Test fun nothingMeasuredToday() {
        assertNull(LiveToday.bytes(emptyList(), emptyList(), day))
        // Yesterday's reading and sample don't count
        assertNull(LiveToday.bytes(listOf(reading(-20, -10, 5_000)), listOf(sample(-60, 1_000)), day))
    }

    @Test fun readingsPlusSamplesAfterTheLatestReading() {
        val readings = listOf(reading(0, 10, 1_000_000), reading(10, 20, 2_000_000))
        // Samples up to 20:00 are already in the readings; two after it are live
        val samples = listOf(sample(19 * 60 + 30, 999), sample(20 * 60, 999), sample(20 * 60 + 30, 4_000), sample(21 * 60, 6_000))
        assertEquals(3_010_000L, LiveToday.bytes(readings, samples, day))
    }

    @Test fun theNextReadingReplacesTheSamplesItCovers() {
        val samples = listOf(sample(20 * 60 + 30, 4_000), sample(21 * 60, 6_000))
        val before = LiveToday.bytes(listOf(reading(10, 20, 2_000_000)), samples, day)
        assertEquals(2_010_000L, before)
        // The reading at 30:00 measured 12,000 B; the samples it covers no longer add anything
        val after = LiveToday.bytes(listOf(reading(10, 20, 2_000_000), reading(20, 30, 12_000)), samples, day)
        assertEquals(2_012_000L, after)
    }

    @Test fun aSampleStraddlingTheLatestReadingCountsOnlyAfterIt() {
        // Reading ends at 20:00; the sample spans 19:45–20:15 with 3,000 B, so half is live
        val straddling = sample(20 * 60 + 15, 3_000)
        assertEquals(1_500L + 1_000L, LiveToday.bytes(listOf(reading(10, 20, 1_000)), listOf(straddling), day))
    }

    @Test fun readingAcrossMidnightIsSplit() {
        // 23:50–00:10 with 2,000 B: half belongs to today
        assertEquals(1_000L, LiveToday.bytes(listOf(reading(-10, 10, 2_000)), emptyList(), day))
    }

    @Test fun samplesAloneWhenNoReadingYet() {
        // Usage Access off or the first 10-minute read not taken yet: the samples since midnight count
        assertEquals(3_000L, LiveToday.bytes(emptyList(), listOf(sample(60, 1_000), sample(90, 2_000)), day))
    }

    @Test fun clockErrorSamplesAreSkipped() {
        assertEquals(1_000L, LiveToday.bytes(emptyList(),
            listOf(sample(60, 1_000), sample(90, 50_000, MeasurementStatus.CLOCK_ERROR)), day))
    }

    @Test fun completeHoursMatchHistoryAtTheHour() {
        // With no samples after it, the live total equals History's day bucket for complete hours
        val readings = (0 until 12).map { reading(it * 10L, it * 10L + 10, 100_000L * (it + 1)) }
        val now = day + 125 * min
        val data = HistoryAggregation.build(emptyList(), readings, emptyMap(), now, zone)
        assertEquals(data.week.last().bytes, LiveToday.bytes(readings, emptyList(), day))
    }

    @Test fun liveValueReplacesTheDayBucketOnHome() {
        val readings = (0 until 6).map { reading(it * 10L, it * 10L + 10, 1_000_000) }
        val data = HistoryAggregation.build(emptyList(), readings, emptyMap(), day + 75 * min, zone)
        val today = HomeInsight.from(data, zone, liveTodayBytes = 7_500_000).first { it.kind == HomeInsight.Kind.TODAY }
        assertEquals("7.5 MB", today.value)
        val fallback = HomeInsight.from(data, zone).first { it.kind == HomeInsight.Kind.TODAY }
        assertEquals("6 MB", fallback.value)
    }

    @Test fun dayStartIsLocalMidnight() {
        assertEquals(day, LiveToday.dayStart(day + 13 * 60 * min + 5, zone))
    }
}
