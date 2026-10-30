package com.capstone.datara.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SyncPreview(val label: String) { PENDING("Pending"), SYNCED("Synced"), OFFLINE("Offline"), ERROR("Error") }
data class SettingsPreviewState(
    val milestonesEnabled: Boolean = true,
    val forecastsEnabled: Boolean = true,
    val sync: SyncPreview = SyncPreview.PENDING
) {
    val pendingItems get() = if (sync == SyncPreview.SYNCED) 0 else 3
}

/**
 * Settings preferences and sync are UI previews: they do not update the account or schedule
 * alerts. The profile name is real and lives in [com.capstone.datara.ui.profile.ProfileViewModel].
 */
@HiltViewModel
class SettingsPreviewViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsPreviewState(
        milestonesEnabled = saved.get<Boolean>("milestones") ?: true,
        forecastsEnabled = saved.get<Boolean>("forecasts") ?: true,
        sync = SyncPreview.entries.firstOrNull { it.name == saved.get<String>("sync") } ?: SyncPreview.PENDING
    ))
    val uiState = mutableState.asStateFlow()

    fun setMilestones(enabled: Boolean) = update(mutableState.value.copy(milestonesEnabled = enabled))
    fun setForecasts(enabled: Boolean) = update(mutableState.value.copy(forecastsEnabled = enabled))
    fun selectSync(sync: SyncPreview) = update(mutableState.value.copy(sync = sync))
    fun previewSync() {
        if (mutableState.value.sync != SyncPreview.OFFLINE) selectSync(SyncPreview.SYNCED)
    }
    private fun update(state: SettingsPreviewState) {
        saved["milestones"] = state.milestonesEnabled
        saved["forecasts"] = state.forecastsEnabled
        saved["sync"] = state.sync.name
        mutableState.value = state
    }
}
