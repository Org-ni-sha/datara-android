package com.capstone.datara

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.capstone.datara.ui.notifications.*
import com.capstone.datara.ui.quota.HomeSampleState
import com.capstone.datara.ui.quota.HomeUiState
import org.junit.Rule
import org.junit.Test

class NotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unreadItemRemainsOpenAfterBeingReadAndCanOpenItsCycle() {
        val state = mutableStateOf(NotificationPreviewState(listOf(
            PreviewAlert("one", "Quota warning", "Sample message", AlertCategory.QUOTA, 1000, false))))
        compose.setContent {
            NotificationsScreen(state.value, HomeUiState(HomeSampleState.QUOTA_REACHED), {}, { id ->
                state.value = NotificationPreviewState(state.value.alerts.map { if (it.id == id) it.copy(isRead = true) else it })
            }, { state.value = NotificationPreviewState(state.value.alerts.map { it.copy(isRead = true) }) })
        }
        compose.captureUi("notifications")
        compose.onNodeWithText("Unread").performScrollTo().performClick()
        compose.onNodeWithText("Quota warning").performScrollTo().performClick()
        compose.onNodeWithText("View sample quota").performClick()
        compose.onNodeWithText("Sample past cycle · Read-only details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End cycle").assertDoesNotExist()
    }

    @Test fun markingAllReadProducesUnreadEmptyStateWithoutRemovingAlerts() {
        val state = mutableStateOf(NotificationPreviewState(listOf(
            PreviewAlert("one", "Quota warning", "Sample message", AlertCategory.QUOTA, 1000, false))))
        compose.setContent { NotificationsScreen(state.value, HomeUiState(), {}, {}, {
            state.value = NotificationPreviewState(state.value.alerts.map { it.copy(isRead = true) })
        }) }
        compose.onNodeWithText("Mark all as read").performScrollTo().performClick()
        compose.onNodeWithText("Unread").performScrollTo().performClick()
        compose.onNodeWithText("No unread notifications").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Quota warning").performScrollTo().assertIsDisplayed()
    }
}
