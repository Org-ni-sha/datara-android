package com.capstone.datara.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.telemetry.TelemetryAccess
import com.capstone.datara.telemetry.TelemetryController
import com.capstone.datara.telemetry.TrackingAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class TrackingStatus(val label: String) {
    OFF("Off"),
    ON("On"),
    /** Left on by the user but not running: killed by Android, or starting up. */
    PAUSED("Paused")
}

/** Pure, so the setup rules can be unit tested without Android. */
data class TrackingSetupUiState(
    val access: TrackingAccess = TrackingAccess(),
    val isRunning: Boolean = false,
    val shouldCollect: Boolean = false
) {
    val status: TrackingStatus
        get() = when {
            isRunning -> TrackingStatus.ON
            shouldCollect -> TrackingStatus.PAUSED
            else -> TrackingStatus.OFF
        }

    val canStart: Boolean get() = access.canStart && !isRunning

    val canStop: Boolean get() = isRunning || shouldCollect

    /** Why Start is disabled, or null when it isn't (or tracking already runs). */
    val startBlockedReason: String?
        get() = when {
            isRunning -> null
            !access.usageAccess && !access.notifications -> "Turn on Usage Access and allow notifications to start."
            !access.usageAccess -> "Turn on Usage Access to start."
            !access.notifications -> "Allow notifications to start."
            else -> null
        }
}

/**
 * Tracking setup: the three grants D4 needs, each through its own Android mechanism, and the
 * start/stop switch. Grants change outside the app (Settings), so the screen calls [refresh] on
 * every resume.
 */
@HiltViewModel
class TrackingSetupViewModel @Inject constructor(
    private val access: TelemetryAccess,
    private val controller: TelemetryController
) : ViewModel() {

    private val grants = MutableStateFlow(access.current())
    private val shouldCollect = MutableStateFlow(controller.shouldCollect)

    val uiState: StateFlow<TrackingSetupUiState> =
        combine(grants, controller.isRunning, shouldCollect) { grants, running, wanted ->
            TrackingSetupUiState(grants, running, wanted)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TrackingSetupUiState(grants.value, controller.isRunning.value, shouldCollect.value)
        )

    fun refresh() {
        grants.value = access.current()
        shouldCollect.value = controller.shouldCollect
    }

    fun start() {
        refresh()
        if (!grants.value.canStart) return
        controller.start()
        shouldCollect.value = true
    }

    fun stop() {
        controller.stop()
        shouldCollect.value = false
    }
}
