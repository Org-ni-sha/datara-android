package com.capstone.datara

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.capstone.datara.ui.history.HistoryScreen
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filtersChangeSummaryAndHistoricalDetailsAreReadOnly() {
        compose.setContent { HistoryScreen({}, {}) }
        compose.onNodeWithText("24 hours").performScrollTo().performClick()
        compose.onNodeWithText("600 MB").assertExists()
        compose.captureUi("history")
        compose.onNodeWithText("View exact readings").performScrollTo().performClick()
        compose.onNodeWithText("23:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Commute quota").performScrollTo().performClick()
        compose.onNodeWithText("Sample past cycle \u00b7 Read-only details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End cycle").assertDoesNotExist()
    }

    @Test fun emptyHistoryCanReturnToSampleHistory() {
        compose.setContent { HistoryScreen({}, {}) }
        compose.onNodeWithContentDescription("Preview options").performClick()
        compose.onNodeWithText("Preview empty history").performClick()
        compose.onNodeWithText("No usage history yet").assertExists()
        compose.onNodeWithContentDescription("Preview options").performClick()
        compose.onNodeWithText("Show sample history").performClick()
        compose.onNodeWithText("7 days").assertExists()
    }
}
