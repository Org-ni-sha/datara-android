package com.capstone.datara

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.capstone.datara.alerts.AlertPreferences
import com.capstone.datara.ui.profile.ProfileScreen
import com.capstone.datara.ui.profile.ProfileUiState
import com.capstone.datara.ui.settings.DIAGNOSTICS_TAPS
import com.capstone.datara.ui.settings.SettingsStatus
import com.capstone.datara.telemetry.TrackingAccess
import com.capstone.datara.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelSignOutDoesNotCallAccountAction() {
        var signOuts = 0
        compose.setContent { SettingsScreen("student@example.com", { null }, {}, {}, {}, {}, { signOuts++ }) }
        compose.captureUi("settings")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, signOuts) }
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Confirm sign out").performClick()
        compose.runOnIdle { assertEquals(1, signOuts) }
    }

    @Test fun profileShowsReadOnlyEmailAndValidatesName() {
        var saves = 0
        compose.setContent {
            SettingsScreen("student@example.com",
                { if (it.isBlank()) "Enter a name" else null.also { saves++ } }, {}, {}, {}, {}, {},
                profile = ProfileUiState(username = "student_01"))
        }
        compose.onNodeWithText("View and edit profile →").performScrollTo().performClick()
        compose.onNodeWithText("student@example.com").assertExists()
        compose.onNodeWithText("@student_01").assertExists()
        compose.captureUi("profile")
        compose.onNodeWithText("Save name").performClick()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Student")
        compose.onNodeWithText("Save name").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun notificationSwitchesShowStoredPreferencesAndReportChanges() {
        val prefs = mutableStateOf(AlertPreferences(milestones = true, forecasts = false))
        compose.setContent {
            SettingsScreen("student@example.com", { null },
                { prefs.value = prefs.value.copy(milestones = it) }, { prefs.value = prefs.value.copy(forecasts = it) },
                {}, {}, {}, alertPreferences = prefs.value)
        }
        compose.onNodeWithText("At 75%, 90% and 100% of your quota").assertExists()
        compose.onNodeWithText("Forecast reminders").performScrollTo()
        compose.onNode(hasText("Quota milestones") and isToggleable(), useUnmergedTree = false).assertIsOn()
        compose.onNode(hasText("Forecast reminders") and isToggleable()).assertIsOff().performClick()
        compose.onNode(hasText("Quota milestones") and isToggleable()).performClick()
        compose.runOnIdle { assertEquals(AlertPreferences(milestones = false, forecasts = true), prefs.value) }
        compose.onNodeWithText("50%", substring = true).assertDoesNotExist()
    }

    @Test fun profileReportsSaveOutcome() {
        val profile = mutableStateOf(ProfileUiState(name = "Student", nameSaved = true))
        compose.setContent { ProfileScreen(profile.value, "student@example.com", {}, { null }) }
        compose.onNodeWithText("Name saved.").performScrollTo().assertIsDisplayed()
        profile.value = ProfileUiState(name = "Student", saveError = "Couldn't save your name.")
        compose.onNodeWithText("Couldn't save your name.").performScrollTo().assertIsDisplayed()
    }

    @Test fun versionTappedSevenTimesOpensDiagnosticsAndNavBarDoesNot() {
        var opened = 0
        compose.setContent {
            SettingsScreen("student@example.com", { null }, {}, {}, {}, {}, {}, onOpenDiagnostics = { opened++ },
                status = SettingsStatus(TrackingAccess(usageAccess = true, notifications = true), serviceRunning = true))
        }
        compose.onNodeWithText("Running").performScrollTo().assertExists()
        compose.onNodeWithText("Not synced yet").performScrollTo().assertExists()
        // The bottom navigation lists no diagnostics entry
        compose.onNodeWithText("diagnostics", substring = true, ignoreCase = true).assertDoesNotExist()
        val version = compose.onNodeWithText("Version", substring = true).performScrollTo()
        repeat(DIAGNOSTICS_TAPS - 1) { version.performClick() }
        compose.runOnIdle { assertEquals(0, opened) }
        version.performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun diagnosticsScreenShowsActiveModelAndSections() {
        compose.setContent {
            com.capstone.datara.ui.diagnostics.DiagnosticsScreen(com.capstone.datara.ui.diagnostics.DiagnosticsUiState(
                loading = false, activeModel = "LOCAL", reason = "Local: its recent MAE is lower.",
                sections = listOf(com.capstone.datara.ui.diagnostics.DiagnosticsSection("Telemetry health",
                    listOf(com.capstone.datara.ui.diagnostics.DiagnosticsRow("Tracking service", "Running"))))), {}, {})
        }
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
        compose.onNodeWithText("Telemetry health").performScrollTo().assertIsDisplayed()
        compose.captureUi("diagnostics")
    }
}
