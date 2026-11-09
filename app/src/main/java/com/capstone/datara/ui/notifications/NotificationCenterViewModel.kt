package com.capstone.datara.ui.notifications

import com.capstone.datara.ui.components.QuotaTone
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.alerts.AlertRules
import com.capstone.datara.alerts.AlertStore
import com.capstone.datara.data.cycle.CycleOwnerProvider
import com.capstone.datara.data.cycle.TrackingCycleRepository
import com.capstone.datara.data.cycle.UsageReadingRepository
import com.capstone.datara.data.local.entity.Notification
import com.capstone.datara.data.local.entity.NotificationType
import com.capstone.datara.domain.cycle.CycleClock
import com.capstone.datara.domain.cycle.CycleMath
import com.capstone.datara.domain.cycle.CycleProgress
import com.capstone.datara.domain.cycle.CycleState
import com.capstone.datara.ui.quota.QuotaDetailsModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [label] is the small tag on an inbox row; the title already says what happened, so it names the kind of alert. */
enum class AlertCategory(val label: String) { QUOTA("Usage milestone"), FORECAST("Forecast alert"), REACHED("Usage milestone") }

/** One inbox row. [cycleId] opens the cycle's details; null for an alert without a cycle. */
data class AlertItem(val id: String, val title: String, val message: String,
    val category: AlertCategory, val receivedAtMillis: Long, val isRead: Boolean, val cycleId: String? = null,
    /** Status colour of a milestone (same code as the ring); null for forecast alerts. */
    val tone: QuotaTone? = null)

data class NotificationCenterState(val alerts: List<AlertItem> = emptyList(), val isLoading: Boolean = false) {
    val unreadCount get() = alerts.count { !it.isRead }
}

/**
 * The in-app notification center over Room `notifications` (D8): the inbox, unread badges and
 * real `read_at` times. Alerts are created by [com.capstone.datara.alerts.QuotaAlertEngine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val owners: CycleOwnerProvider,
    private val store: AlertStore,
    private val cycles: TrackingCycleRepository,
    private val readings: UsageReadingRepository,
    private val clock: CycleClock
) : ViewModel() {

    val uiState: StateFlow<NotificationCenterState> = owners.owner
        .flatMapLatest { owner -> owner?.let { store.observeInbox(it.userId) } ?: flowOf(emptyList()) }
        .map { rows -> NotificationCenterState(rows.map(::toItem)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationCenterState(isLoading = true))

    private val mutableOpenedCycle = MutableStateFlow<QuotaDetailsModel?>(null)

    /** A past cycle opened from an alert, shown read-only; the current cycle opens Quota Details instead. */
    val openedCycle: StateFlow<QuotaDetailsModel?> = mutableOpenedCycle.asStateFlow()

    fun markRead(id: String) {
        viewModelScope.launch { store.markRead(id, clock.now()) }
    }

    fun markAllRead() {
        viewModelScope.launch { owners.owner.first()?.let { store.markAllRead(it.userId, clock.now()) } }
    }

    fun openCycle(cycleId: String) {
        viewModelScope.launch {
            val owner = owners.owner.first() ?: return@launch
            val cycle = cycles.observeAll(owner.userId).first().firstOrNull { it.cycleId == cycleId } ?: return@launch
            val usage = CycleMath.usage(readings.observeReadingsFrom(cycle.startAt).first(), cycle.startAt, cycle.endedAt, cycle.quotaBytes)
            mutableOpenedCycle.value = QuotaDetailsModel.fromCycleState(CycleState.Tracked(CycleProgress(cycle, usage)))
        }
    }

    fun closeCycle() {
        mutableOpenedCycle.value = null
    }

    companion object {
        fun toItem(row: Notification) = AlertItem(
            id = row.notificationId,
            title = row.title,
            message = row.message,
            category = when (row.notificationType) {
                in AlertRules.FORECASTS -> AlertCategory.FORECAST
                NotificationType.QUOTA_REACHED -> AlertCategory.REACHED
                else -> AlertCategory.QUOTA
            },
            receivedAtMillis = row.sentAt,
            isRead = row.readAt != null,
            cycleId = row.cycleId,
            tone = when (row.notificationType) {
                NotificationType.MILESTONE_75 -> QuotaTone.HEADS_UP
                NotificationType.MILESTONE_90 -> QuotaTone.ALMOST
                NotificationType.QUOTA_REACHED -> QuotaTone.REACHED
                else -> null
            }
        )
    }
}
