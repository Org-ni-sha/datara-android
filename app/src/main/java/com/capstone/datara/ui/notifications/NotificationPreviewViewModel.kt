package com.capstone.datara.ui.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.capstone.datara.ui.quota.HomeSampleState
import com.capstone.datara.ui.quota.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class AlertCategory(val label: String) { QUOTA("Quota warning"), FORECAST("Forecast"), INFO("Cycle update") }
data class PreviewAlert(val id: String, val title: String, val message: String,
    val category: AlertCategory, val receivedAtMillis: Long, val isRead: Boolean)
data class NotificationPreviewState(val alerts: List<PreviewAlert>) {
    val unreadCount get() = alerts.count { !it.isRead }
}

/** UI fixtures, independent of actual quota tracking. Read state is session-only. */
@HiltViewModel
class NotificationPreviewViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val anchor: Long = saved.get<Long>("alertAnchor") ?: System.currentTimeMillis()
    private val initialRead = setOf("complete", "half")
    private val definitions = listOf(
        PreviewAlert("complete", "Quota reached", "Your sample Daily quota reached 10 GB. Your internet stayed on.", AlertCategory.INFO, anchor - 900_000, true),
        PreviewAlert("ninety", "90% of your quota used", "9 GB of the 10 GB sample quota had been used, with 1 GB remaining.", AlertCategory.QUOTA, anchor - 3_600_000, false),
        PreviewAlert("forecast", "Your quota was approaching", "At the time of this sample alert, the quota was estimated to be reached in about 4 hours.", AlertCategory.FORECAST, anchor - 7_200_000, false),
        PreviewAlert("eighty", "80% of your quota used", "8 GB of the 10 GB sample quota had been used. This was a reminder to check your usage.", AlertCategory.QUOTA, anchor - 10_800_000, false),
        PreviewAlert("half", "Halfway through your quota", "5 GB of the 10 GB sample quota had been used.", AlertCategory.QUOTA, anchor - 90_000_000, true)
    )
    private val mutableState = MutableStateFlow(NotificationPreviewState(definitions.map {
        it.copy(isRead = it.id in (saved.get<ArrayList<String>>("readAlertIds")?.toSet() ?: initialRead))
    }))
    val uiState = mutableState.asStateFlow()
    val sampleCycle = HomeUiState(HomeSampleState.QUOTA_REACHED, 10_000_000_000, "Daily quota · sample",
        startedAtMillis = anchor - 604_800_000, endedAtMillis = anchor - 900_000)

    init { saved["alertAnchor"] = anchor }

    fun markRead(id: String) {
        update(mutableState.value.alerts.map { if (it.id == id) it.copy(isRead = true) else it })
    }

    fun markAllRead() = update(mutableState.value.alerts.map { it.copy(isRead = true) })

    private fun update(alerts: List<PreviewAlert>) {
        saved["readAlertIds"] = ArrayList(alerts.filter { it.isRead }.map { it.id })
        mutableState.value = NotificationPreviewState(alerts)
    }
}
