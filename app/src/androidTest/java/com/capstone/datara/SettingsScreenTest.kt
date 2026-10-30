package com.capstone.datara

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.capstone.datara.ui.profile.ProfileScreen
import com.capstone.datara.ui.profile.ProfileUiState
import com.capstone.datara.ui.settings.SettingsPreviewState
import com.capstone.datara.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelSignOutDoesNotCallAccountAction() {
        var signOuts = 0
        compose.setContent { SettingsScreen(SettingsPreviewState(), "student@example.com", { null }, {}, {}, {}, {}, {}, {}, { signOuts++ }) }
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
            SettingsScreen(SettingsPreviewState(), "student@example.com",
                { if (it.isBlank()) "Enter a name" else null.also { saves++ } }, {}, {}, {}, {}, {}, {}, {},
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

    @Test fun profileReportsSaveOutcome() {
        val profile = mutableStateOf(ProfileUiState(name = "Student", nameSaved = true))
        compose.setContent { ProfileScreen(profile.value, "student@example.com", {}, { null }) }
        compose.onNodeWithText("Name saved.").performScrollTo().assertIsDisplayed()
        profile.value = ProfileUiState(name = "Student", saveError = "Couldn't save your name.")
        compose.onNodeWithText("Couldn't save your name.").performScrollTo().assertIsDisplayed()
    }
}
