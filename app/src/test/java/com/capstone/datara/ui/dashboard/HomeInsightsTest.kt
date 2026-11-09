package com.capstone.datara.ui.dashboard

import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.telemetry.MeasurementStatus
import com.capstone.datara.ui.history.HistoryAggregation
import com.capstone.datara.ui.history.HistoryData
import com.capstone.datara.ui.history.HistoryWindow
import com.capstone.datara.ui.quota.formatQuotaData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HomeInsightsTest {
    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val day0 = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun hourReadings(h: Long, perReading: Long) = (0 until 6).map { i ->
        val since = day0 + h * hour + i * 600_000L
        DurableUsageReading(since = since, until = since + 600_000L, bytesRx = perReading, bytesTx = 0, status = MeasurementStatus.OK, schemaVersion = 4)
    }

    @Test fun numbersMatchHistory() {
        // Usage on Nov 3, 4 and 6; "now" is Nov 6 15:30
        val readings = hourReadings(50, 1_000_000) + hourReadings(75, 500_000) + hourReadings(122, 2_000_000) + hourReadings(126, 300_000)
        val now = day0 + 5 * 24 * hour + 15 * hour + 30 * 60_000
        val reachedAt = day0 + 80 * hour
        val cycle = TrackingCycle("c1", "u", "d", "Old", 10_000_000, day0 + 40 * hour, reachedAt, null, CycleStatus.REACHED, day0, day0)
        val forecast = Forecast("f", "u", "d", "c1", reachedAt - 30 * hour, 0, ActiveModel.GLOBAL, 0, reachedAt - 5 * hour, null, null, "v0", "{}")
        val data = HistoryAggregation.build(listOf(cycle), readings, mapOf("c1" to listOf(forecast)), now, zone)
        val insights = HomeInsight.from(data, zone).associateBy { it.kind }

        assertEquals(formatQuotaData(data.week.last().bytes!!), insights[HomeInsight.Kind.TODAY]!!.value)
        assertEquals(formatQuotaData(13_800_000), insights[HomeInsight.Kind.TODAY]!!.value)
        val week = HistoryAggregation.snapshot(HistoryWindow.WEEK, data, null, zone)
        assertEquals(formatQuotaData(week.averageDailyBytes), insights[HomeInsight.Kind.DAILY_AVERAGE]!!.value)
        val dayPeak = HistoryAggregation.snapshot(HistoryWindow.DAY, data, null, zone).peak!!
        assertEquals(dayPeak.label, insights[HomeInsight.Kind.PEAK_HOUR]!!.value)
        assertEquals("02:00", insights[HomeInsight.Kind.PEAK_HOUR]!!.value)
        assertEquals("5 h", insights[HomeInsight.Kind.FORECAST_VS_ACTUAL]!!.value)
        assertEquals("Later than forecast, last quota", insights[HomeInsight.Kind.FORECAST_VS_ACTUAL]!!.label)
    }

    @Test fun missingDataSaysSoAndNoReachedCycleHidesComparison() {
        val insights = HomeInsight.from(HistoryAggregation.build(emptyList(), emptyList(), emptyMap(), day0 + 30 * hour, zone), zone)
        assertEquals(3, insights.size)
        insights.forEach {
            assertNull(it.value)
            assertEquals(HomeInsight.NOT_ENOUGH, it.label)
        }
    }

    @Test fun offsets() {
        assertEquals("Within 1 h", HomeInsight.offset(20 * 60_000))
        assertEquals("3 h", HomeInsight.offset(-3 * hour))
        assertEquals("2 days", HomeInsight.offset(50 * hour))
    }

    @Test fun emptyHistoryData() {
        assertEquals(3, HomeInsight.from(HistoryData(emptyList(), emptyList(), emptyList(), 0), zone).size)
    }
}
