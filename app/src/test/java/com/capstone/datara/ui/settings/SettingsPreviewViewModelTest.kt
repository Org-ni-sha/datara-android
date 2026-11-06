package com.capstone.datara.ui.settings

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class SettingsPreviewViewModelTest {
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
