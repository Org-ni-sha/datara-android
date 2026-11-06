package com.capstone.datara

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.capstone.datara.ui.notifications.*
import com.capstone.datara.ui.quota.QuotaDetailsModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationsScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun alert(read: Boolean = false) = AlertItem("one", "90% of your quota used",
        "900 MB of 1 GB used on “Everyday”. Only 100 MB left. Nothing is blocked.", AlertCategory.QUOTA, 1000, read, "c1")

    @Test fun tappingAnAlertMarksItReadAndOpensItsCycle() {
        val state = mutableStateOf(NotificationCenterState(listOf(alert())))
        val opened = mutableStateOf<QuotaDetailsModel?>(null)
        val openedIds = mutableListOf<String>()
        compose.setContent {
            NotificationsScreen(state.value, {}, { id ->
                state.value = NotificationCenterState(state.value.alerts.map { if (it.id == id) it.copy(isRead = true) else it })
            }, { state.value = NotificationCenterState(state.value.alerts.map { it.copy(isRead = true) }) },
                onOpenCycle = { id ->
                    openedIds += id
                    opened.value = QuotaDetailsModel(hasQuota = true, name = "Everyday", quotaBytes = 1_000_000_000,
                        usedBytes = 1_000_000_000, remainingBytes = 0, progress = 1f, startedAtMillis = 0,
                        endedAtMillis = 5_000, isReached = true, cycleId = id)
                }, openedCycle = opened.value, onCloseCycle = { opened.value = null })
        }
        compose.captureUi("notifications")
        compose.onNodeWithText("1 unread update").assertExists()
        compose.onNodeWithText("Unread").performScrollTo().performClick()
        compose.onNodeWithText("90% of your quota used").performScrollTo().performClick()
        compose.onNodeWithText("View quota details").performClick()
        compose.runOnIdle { assertEquals(listOf("c1"), openedIds) }
        compose.onNodeWithText("Opened from an alert · Read-only details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End cycle").assertDoesNotExist()
        compose.onNodeWithText("Create another quota").assertDoesNotExist()
    }

    @Test fun markingAllReadProducesUnreadEmptyStateWithoutRemovingAlerts() {
        val state = mutableStateOf(NotificationCenterState(listOf(alert())))
        compose.setContent { NotificationsScreen(state.value, {}, {}, {
            state.value = NotificationCenterState(state.value.alerts.map { it.copy(isRead = true) })
        }) }
        compose.onNodeWithText("Mark all as read").performScrollTo().performClick()
        compose.onNodeWithText("You’re all caught up.").assertExists()
        compose.onNodeWithText("Unread").performScrollTo().performClick()
        compose.onNodeWithText("No unread notifications").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("90% of your quota used").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyInboxExplainsTheAlertsAndOffersNoSampleData() {
        compose.setContent { NotificationsScreen(NotificationCenterState(), {}, {}, {}) }
        compose.onNodeWithText("No notifications yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75%, 90% and 100%", substring = true).assertExists()
        compose.onNodeWithText("Sample", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Mark all as read").assertIsNotEnabled()
    }
}
