package com.capstone.datara

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.setContent { SettingsScreen(SettingsPreviewState(), "student@example.com", { if (it.isBlank()) "Enter a name" else null }, {}, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("View and edit preview profile →").performScrollTo().performClick()
        compose.onNodeWithText("student@example.com").assertExists()
        compose.captureUi("profile")
        compose.onNodeWithText("Save preview name").performClick()
        compose.onNodeWithText("Enter a name").assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Student")
        compose.onNodeWithText("Save preview name").performClick()
        compose.onNodeWithText("Preview name saved for this session.").performScrollTo().assertIsDisplayed()
    }
}
