package com.capstone.datara.ui.history

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.telemetry.MeasurementStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HistoryAggregationTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val day0 = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Six 10-minute readings covering hour [h] (hours after day0), [perReading] bytes each. */
    private fun hourReadings(h: Long, perReading: Long, status: String = MeasurementStatus.OK) = (0 until 6).map { i ->
        val since = day0 + h * hour + i * 600_000L
        DurableUsageReading(since = since, until = since + 600_000L, bytesRx = perReading, bytesTx = 0, status = status, schemaVersion = 4)
    }

    private fun cycle(id: String, start: Long, quota: Long, reachedAt: Long? = null, endedAt: Long? = null) = TrackingCycle(
        id, "u", "d", null, quota, start, reachedAt, endedAt,
        if (reachedAt != null) CycleStatus.REACHED else if (endedAt != null) CycleStatus.ENDED else CycleStatus.ACTIVE, start, start)

    private fun forecast(cycleId: String, at: Long, globalReach: Long?, localReach: Long? = null, model: String = ActiveModel.GLOBAL) =
        Forecast("f$at", "u", "d", cycleId, at, 0, model, 0, globalReach, null, localReach, "v0", "{}")

    @Test fun unmeasuredHoursAreNullNotZero() {
        val measured = HistoryAggregation.measuredHours(hourReadings(0, 1_000_000) + hourReadings(2, 0))
        val hours = HistoryAggregation.hours(measured, day0, day0 + 3 * hour)
        assertEquals(listOf(6_000_000L, null, 0L), hours.map { it.bytes })
    }

    @Test fun hourWithAnUnusableReadingIsNotMeasured() {
        val readings = hourReadings(0, 1_000_000).mapIndexed { i, r -> if (i == 3) r.copy(status = MeasurementStatus.CLOCK_ERROR) else r }
        assertTrue(HistoryAggregation.measuredHours(readings).isEmpty())
    }

    @Test fun dailyTotalsSumMeasuredHoursAndCountGaps() {
        val readings = hourReadings(1, 500_000) + hourReadings(5, 500_000) + hourReadings(30, 100_000)
        val measured = HistoryAggregation.measuredHours(readings)
        val hours = HistoryAggregation.hours(measured, day0, day0 + 48 * hour)
        val days = HistoryAggregation.days(hours, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), zone)
        assertEquals(3, days.size)
        assertEquals(6_000_000L, days[0].bytes)
        assertEquals(2, days[0].measuredHours)
        assertEquals(24, days[0].totalHours)
        assertEquals(600_000L, days[1].bytes)
        assertNull("no hours at all is a gap", days[2].bytes)
    }

    @Test fun snapshotAveragesOverMeasuredTimeOnly() {
        val snapshot = HistorySnapshot(listOf(
            UsagePoint("a", 12_000_000, measuredHours = 12, totalHours = 24),
            UsagePoint("b", null, measuredHours = 0, totalHours = 24)
        ), 2)
        assertEquals(12_000_000L, snapshot.totalBytes)
        assertEquals(24_000_000L, snapshot.averageDailyBytes)
        assertTrue(snapshot.isPartial)
        assertEquals("a", snapshot.peak?.label)
    }

    @Test fun buildShowsCycleProgressAndWeekWindow() {
        val now = day0 + 50 * hour + 15 * 60_000
        val c = cycle("c1", day0 + 24 * hour, quota = 10_000_000)
        val data = HistoryAggregation.build(listOf(c), hourReadings(25, 500_000) + hourReadings(49, 0), emptyMap(), now, zone)
        assertEquals(24, data.day.size)
        assertEquals(7, data.week.size)
        assertEquals(LocalDate.of(2026, 11, 3), data.week.last().date)
        val item = data.cycles.single()
        assertEquals(3_000_000L, item.usage.usedBytes)
        assertEquals(0.3f, item.toDetails().progress, 1e-6f)
        assertEquals("Active", item.statusLabel)
        assertEquals(listOf(3_000_000L, 0L), item.days.map { it.bytes })
        assertEquals(2, data.measuredHours)
        assertFalse(data.isEmpty)
    }

    @Test fun currentPartialHourIsLeftOut() {
        val now = day0 + 10 * hour + 30 * 60_000
        val data = HistoryAggregation.build(emptyList(), hourReadings(10, 1_000), emptyMap(), now, zone)
        assertEquals(0, data.measuredHours)
        assertTrue(data.isEmpty)
    }

    @Test fun petComparisonPrefersForecastADayBeforeTheReach() {
        val reached = day0 + 72 * hour
        val c = cycle("c1", day0, 1, reachedAt = reached, endedAt = reached)
        val forecasts = listOf(
            forecast("c1", day0 + 10 * hour, day0 + 60 * hour),
            forecast("c1", day0 + 40 * hour, day0 + 70 * hour, localReach = day0 + 75 * hour, model = ActiveModel.LOCAL),
            forecast("c1", day0 + 60 * hour, day0 + 71 * hour)
        )
        val pet = HistoryAggregation.petComparison(c, forecasts)!!
        assertEquals(day0 + 40 * hour, pet.predictedAt)
        assertEquals(day0 + 75 * hour, pet.predictedReachAt)
        assertEquals(-3 * hour, pet.errorMillis)
    }

    @Test fun petComparisonFallsBackToEarliestAndNeedsAReach() {
        val reached = day0 + 5 * hour
        val c = cycle("c1", day0, 1, reachedAt = reached)
        val pet = HistoryAggregation.petComparison(c, listOf(forecast("c1", day0 + 2 * hour, day0 + 4 * hour), forecast("c1", day0 + hour, null)))!!
        assertEquals(day0 + 2 * hour, pet.predictedAt)
        assertNull(HistoryAggregation.petComparison(cycle("c2", day0, 1), listOf(forecast("c2", day0, day0 + hour))))
    }

    @Test fun petErrorWording() {
        assertEquals("On time", petErrorText(0))
        assertEquals("3 h later than predicted", petErrorText(3 * hour))
        assertEquals("45 min earlier than predicted", petErrorText(-45 * 60_000L))
    }
}
