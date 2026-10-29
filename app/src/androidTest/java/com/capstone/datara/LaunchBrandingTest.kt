package com.capstone.datara

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.capstone.datara.ui.splash.SplashScreen
import com.capstone.datara.ui.theme.DATAraTheme
import org.junit.Rule
import org.junit.Test

class LaunchBrandingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionLoadingRemainsReadableWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DATAraTheme { SplashScreen() }
            }
        }
        compose.onNodeWithText("DATAra").assertIsDisplayed()
        compose.onNodeWithText("Restoring your session\u2026").assertIsDisplayed()
        compose.captureUi("launch-loading")
    }
}
