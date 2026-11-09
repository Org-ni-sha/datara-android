package com.capstone.datara

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.capstone.datara.data.cycle.CycleStatus
import com.capstone.datara.data.cycle.TrackingCycle
import com.capstone.datara.data.telemetry.DurableUsageReading
import com.capstone.datara.telemetry.MeasurementStatus
import com.capstone.datara.ui.history.HistoryAggregation
import com.capstone.datara.ui.history.HistoryData
import com.capstone.datara.ui.history.HistoryScreen
import com.capstone.datara.ui.history.HistoryUiState
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val now = System.currentTimeMillis() / hour * hour + 5 * 60_000

    /** Test fixture: hours [hoursAgo] measured at 1 MB per 10 minutes; one reached cycle. */
    private fun populated(): HistoryUiState {
        val readings = listOf(2L, 3L, 5L).flatMap { ago ->
            (0 until 6).map { i ->
                val since = now / hour * hour - ago * hour + i * 600_000L
                DurableUsageReading(since = since, until = since + 600_000L, bytesRx = 1_000_000, bytesTx = 0,
                    status = MeasurementStatus.OK, schemaVersion = 4)
            }
        }
        val start = now - 6 * hour
        val reached = now - 2 * hour
        val cycle = TrackingCycle("c1", "u", "d", "Commute quota", 10_000_000, start, reached, reached, CycleStatus.REACHED, start, start)
        return HistoryUiState.Ready(HistoryAggregation.build(listOf(cycle), readings, emptyMap(), now, zone))
    }

    private fun setContent(state: HistoryUiState, fontScale: Float = 1f) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            HistoryScreen(state, {}, {}, zone = zone)
        }
    }

    @Test fun emptyHistoryExplainsHowItFillsIn() {
        setContent(HistoryUiState.Ready(HistoryData(emptyList(), emptyList(), emptyList(), 0)))
        compose.onNodeWithText("No usage history yet").assertIsDisplayed()
        compose.onNodeWithText("Preview options").assertDoesNotExist()
        compose.onNodeWithText("Illustrative history").assertDoesNotExist()
    }

    @Test fun emptyHistoryAtLargeFont() {
        setContent(HistoryUiState.Ready(HistoryData(emptyList(), emptyList(), emptyList(), 0)), fontScale = 2f)
        compose.onNodeWithText("Go to Home").performScrollTo().assertIsDisplayed()
    }

    @Test fun populatedHistoryShowsGapsAndReadOnlyCycleDetails() {
        setContent(populated())
        compose.onNodeWithText("Collecting: 3 hours measured so far. Charts fill in as tracking runs.").assertIsDisplayed()
        compose.onNodeWithText("24 hours").performScrollTo().performClick()
        compose.onNodeWithText("18 MB").performScrollTo().assertIsDisplayed()
        compose.captureUi("history")
        compose.onNodeWithText("View exact readings").performScrollTo().performClick()
        compose.onAllNodesWithText("Not measured").onFirst().assertExists()
        compose.onAllNodesWithText("6 MB").onFirst().assertExists()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Quota cycles").performScrollTo().performClick()
        compose.onNodeWithText("24 hours").assertDoesNotExist()
        compose.onAllNodesWithText("QUOTA REACHED").onFirst().assertExists()
        compose.onNodeWithText("All cycles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View cycle details").performScrollTo().performClick()
        compose.onNodeWithText("From History · Read-only details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End cycle").assertDoesNotExist()
    }

    @Test fun populatedHistoryAtLargeFont() {
        setContent(populated(), fontScale = 2f)
        compose.onNodeWithText("Quota cycles").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View exact readings").performScrollTo().assertIsDisplayed()
    }
}
