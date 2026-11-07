package com.capstone.datara.ui.diagnostics

import com.capstone.datara.data.local.entity.ActiveModel
import com.capstone.datara.data.local.entity.Forecast
import com.capstone.datara.data.local.entity.UserModelState
import com.capstone.datara.forecast.FeatureContract
import com.capstone.datara.forecast.ForecastEngine
import com.capstone.datara.forecast.ForecastStatus
import com.capstone.datara.forecast.InferenceTiming
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class DiagnosticsMapperTest {
    private val zone: ZoneId = ZoneOffset.ofHours(8)
    private val hour = 1_762_480_800_000L // 2025-11-07 02:00 UTC
    private val userId = "11111111-2222-3333-4444-555555555555"
    private val deviceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    private fun state(active: String = ActiveModel.GLOBAL, hours: Int = 40, g: Double? = 2_000_000.0, l: Double? = 1_900_000.0) =
        UserModelState(userId = userId, activeModel = active, deviceId = deviceId, hoursSeen = hours,
            globalRecentMae = g, localRecentMae = l, updatedAt = hour)

    private fun forecast(local: Boolean = false): Forecast {
        val x = FloatArray(FeatureContract.N_FEATURES) { it.toFloat() / 10 }.also { it[9] = Float.NaN }
        return Forecast(forecastId = "f", userId = userId, deviceId = deviceId, cycleId = "cccccccc-0000-0000-0000-000000000000",
            predictedAt = hour, remainingBytes = 820_000_000, activeModel = if (local) ActiveModel.LOCAL else ActiveModel.GLOBAL,
            globalNextHourBytes = 12_300_000, globalReachAt = hour + 30 * FeatureContract.HOUR_MS,
            localNextHourBytes = if (local) 10_000_000 else null, localReachAt = if (local) hour + 40 * FeatureContract.HOUR_MS else null,
            modelVersion = "v0-tweedie-pet-2026-11-07", inputs = ForecastEngine.inputsJson(x, zone, hour))
    }

    private fun snapshot(modelState: UserModelState? = state(), forecasts: List<Forecast> = listOf(forecast())) = DiagnosticsSnapshot(
        now = hour + 15 * 60_000, zone = zone, signedIn = true, modelState = modelState, forecasts = forecasts,
        model = ModelInfo("v0-tweedie-pet-2026-11-07", "identity", 1.0, 4),
        timing = InferenceTiming(hour, 850, 42, 30), forecastStatus = ForecastStatus.Pending,
        lastSampleEnd = hour + 14 * 60_000, lastReadingUntil = hour + 10 * 60_000, samplesLast24h = 2_000,
        measuredHoursLast24h = 20, serviceRunning = true)

    private fun DiagnosticsUiState.value(label: String) = sections.flatMap { it.rows }.first { it.label == label }

    @Test fun beforeSevenDaysGlobalIsExplainedByHoursLearned() {
        val ui = DiagnosticsMapper.map(snapshot())
        assertEquals(ActiveModel.GLOBAL, ui.activeModel)
        assertTrue(ui.reason, ui.reason.contains("40 of 168 hours"))
        assertEquals(40f / 168, ui.learnedProgress, 1e-6f)
        assertTrue(ui.value("Hours learned").warning)
    }

    @Test fun afterSevenDaysTheFivePercentRuleIsShown() {
        // 5% lower is not enough (needs ≥ 5%, strictly below 95% of Global)
        val notEnough = DiagnosticsMapper.reason(ActiveModel.GLOBAL, 200, 2_000_000.0, 1_960_000.0, true, true)
        assertTrue(notEnough, notEnough.contains("2% lower") && notEnough.contains("at least 5% lower"))
        val worse = DiagnosticsMapper.reason(ActiveModel.GLOBAL, 200, 2_000_000.0, 2_200_000.0, true, true)
        assertTrue(worse, worse.contains("10% higher"))
        val local = DiagnosticsMapper.reason(ActiveModel.LOCAL, 200, 2_000_000.0, 1_500_000.0, true, true)
        assertTrue(local, local.startsWith("Local") && local.contains("1.50 MB") && local.contains("2.00 MB"))
        assertTrue(DiagnosticsMapper.reason(ActiveModel.GLOBAL, 200, 2.0, 1.0, false, true).contains("no usage ratios"))
    }

    @Test fun latestForecastShowsBothModelsAndContract() {
        val ui = DiagnosticsMapper.map(snapshot(forecasts = listOf(forecast(local = true))))
        assertEquals("12.30 MB", ui.value("Global next hour").value)
        assertEquals("10.00 MB", ui.value("Local next hour").value)
        assertEquals("820.00 MB", ui.value("Remaining quota").value)
        assertEquals("Nov 8, 16:00", ui.value("Global reach time").value)
        assertEquals("4", ui.value("Contract version").value)
        assertEquals("v0-tweedie-pet-2026-11-07", ui.value("Model version").value)
        assertEquals("identity × 1", ui.value("Output transform").value)
        assertEquals("0.85 ms", ui.value("One prediction").value)
        assertEquals("42 ms for 30 hourly predictions", ui.value("PET projection").value)
        assertEquals(1, ui.table.size)
        assertEquals("10.00", ui.table[0].localMb)
    }

    @Test fun allTwelveInputsAreListedAndMissingOnesFlagged() {
        val inputs = DiagnosticsMapper.map(snapshot()).sections.first { it.title.startsWith("Input features") }.rows
        assertEquals(FeatureContract.FEATURE_NAMES, inputs.map { it.label })
        assertEquals("0.4000", inputs[4].value)
        assertEquals("missing (NaN)", inputs[9].value)
        assertTrue(inputs[9].warning)
    }

    @Test fun telemetryHealthAndNoForecastYet() {
        val ui = DiagnosticsMapper.map(snapshot(modelState = null, forecasts = emptyList()))
        assertEquals("1 min ago", ui.value("Last 30 s sample").value)
        assertFalse(ui.value("Last 30 s sample").warning)
        assertEquals("None logged yet", ui.value("Forecast rows").value)
        assertEquals("20 / 24 (forecast needs 6)", ui.value("Measured hours, last 24 h").value)
        assertTrue(ui.table.isEmpty())
    }

    @Test fun noUuidIsEverShown() {
        val ui = DiagnosticsMapper.map(snapshot(forecasts = listOf(forecast(local = true))))
        val text = (ui.sections.flatMap { s -> s.rows.flatMap { listOf(it.label, it.value) } + s.title } + ui.reason +
            ui.table.flatMap { listOf(it.hour, it.active, it.globalMb, it.localMb, it.remainingMb) }).joinToString("\n")
        assertFalse(Regex("[0-9a-f]{8}-[0-9a-f]{4}-").containsMatchIn(text))
    }
}
