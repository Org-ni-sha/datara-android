package com.capstone.datara

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.capstone.datara.ui.dashboard.HomeScreen
import com.capstone.datara.ui.quota.*
import com.capstone.datara.ui.theme.DATAraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class QuotaScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enlargedHomeKeepsDetailsReachable() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                HomeScreen(HomeUiState(HomeSampleState.FORECAST_AVAILABLE), {}, {}, {})
            }
        }
        compose.onNodeWithText("About 12 hours").assertExists()
        compose.onNodeWithText("View quota details").performScrollTo().assertIsDisplayed()
        compose.captureUi("home-large-text")
    }

    @Test fun detailsRequireConfirmationAndOfferAnotherQuotaAfterEnding() {
        val state = mutableStateOf(HomeUiState(HomeSampleState.FORECAST_AVAILABLE))
        var starts = 0
        compose.setContent { DATAraTheme {
            QuotaDetailsScreen(state.value, {}, { state.value = state.value.copy(endedAtMillis = 2000) }, { starts++ })
        } }
        compose.onNodeWithText("End cycle").performScrollTo().performClick()
        compose.onNodeWithText("Keep tracking").performClick()
        compose.onNodeWithText("Cycle ended").assertDoesNotExist()
        compose.onNodeWithText("End cycle").performScrollTo().performClick()
        compose.onNode(hasText("End cycle") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Forecast stopped").assertExists()
        compose.onNodeWithText("Create another quota").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun sampleStatesRenderWithoutShowingFakeBalancesForMissingPermission() {
        val state = mutableStateOf(HomeUiState())
        compose.setContent { DATAraTheme { HomeScreen(state.value, {}, {}, {}) } }
        compose.onNodeWithText("Create a quota").assertExists()
        capture("home-empty")
        compose.runOnIdle { state.value = HomeUiState(HomeSampleState.COLLECTING) }
        compose.onNodeWithText("Waiting for sample readings").assertExists()
        compose.onNodeWithText("Usage trend pending").assertExists()
        capture("home-collecting")
        compose.onNodeWithText("Last 7 days").assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUiState(HomeSampleState.FORECAST_AVAILABLE) }
        compose.onNodeWithText("About 12 hours").assertExists()
        compose.onNodeWithText("Last 7 days").assertExists()
        compose.onNodeWithText("6 GB remaining").assertExists()
        capture("home-forecast")
        compose.runOnIdle { state.value = HomeUiState(HomeSampleState.MISSING_PERMISSION) }
        compose.onNodeWithText("About Usage Access").assertExists()
        compose.onNodeWithText("Remaining quota unavailable").assertExists()
        compose.onNodeWithText("Last 7 days").assertDoesNotExist()
        capture("home-access")
        compose.onNodeWithText("6 GB").assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUiState(HomeSampleState.QUOTA_REACHED) }
        compose.onNodeWithText("Create another quota").assertExists()
        capture("home-reached")
    }

    @Test fun quotaFormValidatesAndSubmitsDecimalGb() {
        var submitted = 0L
        compose.setContent { DATAraTheme { CreateQuotaScreen({}, { bytes, _ -> submitted = bytes }) } }
        compose.captureUi("create-quota")
        compose.onNodeWithText("Start tracking").performClick()
        compose.onNodeWithText("Enter the amount you want to track.").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("1.5")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithText("Start tracking").performClick()
        compose.runOnIdle { assertEquals(1_500_000_000L, submitted) }
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("ui-review")!!
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
