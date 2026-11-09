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

    /** A real-cycle model 40% into a 10 GB quota (Preview options and its samples are gone). */
    private val cycle = QuotaDetailsModel(hasQuota = true, name = "Everyday data", quotaBytes = 10_000_000_000L,
        usedBytes = 4_000_000_000L, remainingBytes = 6_000_000_000L, progress = 0.4f, startedAtMillis = 1000)

    @Test fun enlargedHomeKeepsDetailsReachable() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                HomeScreen({}, {}, live = cycle)
            }
        }
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithText("View quota details").performScrollTo().assertIsDisplayed()
        compose.captureUi("home-large-text")
    }

    @Test fun detailsRequireConfirmationAndOfferAnotherQuotaAfterEnding() {
        val state = mutableStateOf(cycle)
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

    @Test fun homeShowsEmptyLoadingCycleAndReachedStates() {
        val live = mutableStateOf<QuotaDetailsModel?>(null)
        val loading = mutableStateOf(false)
        compose.setContent { DATAraTheme { HomeScreen({}, {}, live = live.value, liveLoading = loading.value) } }
        compose.onNodeWithText("Create a quota").assertExists()
        compose.onNodeWithText("Preview options").assertDoesNotExist()
        capture("home-empty")
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithText("Loading your quota…").assertExists()
        compose.runOnIdle { loading.value = false; live.value = cycle }
        compose.onNodeWithText("40%").assertExists()
        compose.onNodeWithText("View quota details").performScrollTo().assertIsDisplayed()
        capture("home-cycle")
        compose.runOnIdle { live.value = cycle.copy(usedBytes = 10_000_000_000L, remainingBytes = 0, progress = 1f, isReached = true) }
        compose.onNodeWithText("Create another quota").performScrollTo().assertExists()
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
