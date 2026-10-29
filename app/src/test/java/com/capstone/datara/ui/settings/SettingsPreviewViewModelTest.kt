package com.capstone.datara.ui.settings

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class SettingsPreviewViewModelTest {
    @Test fun invalidNamesLeavePreviousNameUntouched() {
        val model = SettingsPreviewViewModel(SavedStateHandle())
        assertNull(model.saveName("  Student  "))
        listOf("", "x", "a".repeat(61), "Hi\nthere").forEach { assertNotNull(model.saveName(it)) }
        assertEquals("Student", model.uiState.value.displayName)
    }

    @Test fun preferencesAndNameSurviveRecreation() {
        val saved = SavedStateHandle()
        val model = SettingsPreviewViewModel(saved)
        model.saveName("  Student Name  ")
        model.setMilestones(false)
        model.setForecasts(false)
        val restored = SettingsPreviewViewModel(saved).uiState.value
        assertEquals("Student Name", restored.displayName)
        assertFalse(restored.milestonesEnabled)
        assertFalse(restored.forecastsEnabled)
    }

    @Test fun offlinePreviewRetainsPendingItemsAndRetryCanSucceed() {
        val saved = SavedStateHandle()
        val model = SettingsPreviewViewModel(saved)
        model.selectSync(SyncPreview.OFFLINE)
        model.previewSync()
        assertEquals(SyncPreview.OFFLINE, model.uiState.value.sync)
        assertEquals(3, model.uiState.value.pendingItems)
        model.selectSync(SyncPreview.ERROR)
        model.previewSync()
        assertEquals(SyncPreview.SYNCED, SettingsPreviewViewModel(saved).uiState.value.sync)
        assertEquals(0, model.uiState.value.pendingItems)
    }
}
