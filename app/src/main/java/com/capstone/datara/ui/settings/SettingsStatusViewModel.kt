package com.capstone.datara.ui.settings

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

/** What Settings shows about tracking: the real Android grants and whether the service runs. */
data class SettingsStatus(val access: TrackingAccess = TrackingAccess(), val serviceRunning: Boolean = false)

/**
 * The real tracking status for Settings (D9). Grants change in Android Settings, outside the app,
 * so the screen calls [refresh] on every resume. Cloud sync (C3) is not built; Settings says so.
 */
@HiltViewModel
class SettingsStatusViewModel @Inject constructor(
    private val access: TelemetryAccess,
    controller: TelemetryController
) : ViewModel() {
    private val grants = MutableStateFlow(access.current())
    val status: StateFlow<SettingsStatus> = combine(grants, controller.isRunning, ::SettingsStatus)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStatus(grants.value, controller.isRunning.value))

    fun refresh() { grants.value = access.current() }
}
