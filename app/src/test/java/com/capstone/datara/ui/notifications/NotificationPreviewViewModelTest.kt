package com.capstone.datara.ui.notifications

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class NotificationPreviewViewModelTest {
    @Test fun individualReadStateAndTimestampSurviveRestoration() {
        val saved = SavedStateHandle()
        val model = NotificationPreviewViewModel(saved)
        assertEquals(3, model.uiState.value.unreadCount)
        model.markRead("ninety")
        model.markRead("ninety")
        model.markRead("missing-id")
        assertEquals(2, model.uiState.value.unreadCount)
        val restored = NotificationPreviewViewModel(saved)
        assertEquals(model.uiState.value, restored.uiState.value)
        assertEquals(model.sampleCycle, restored.sampleCycle)
        assertEquals(5, restored.uiState.value.alerts.size)
    }

    @Test fun markAllReadPreservesAlertsAndIsIdempotent() {
        val saved = SavedStateHandle()
        val model = NotificationPreviewViewModel(saved)
        val messages = model.uiState.value.alerts.map { it.message }
        model.markAllRead()
        model.markAllRead()
        val restored = NotificationPreviewViewModel(saved)
        assertEquals(0, restored.uiState.value.unreadCount)
        assertEquals(messages, restored.uiState.value.alerts.map { it.message })
        assertTrue(restored.uiState.value.alerts.all { it.isRead })
    }
}
