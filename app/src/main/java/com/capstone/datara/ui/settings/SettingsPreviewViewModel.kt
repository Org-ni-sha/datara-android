package com.capstone.datara.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SyncPreview(val label: String) { PENDING("Pending"), SYNCED("Synced"), OFFLINE("Offline"), ERROR("Error") }
data class SettingsPreviewState(
    val displayName: String = "",
    val milestonesEnabled: Boolean = true,
    val forecastsEnabled: Boolean = true,
    val sync: SyncPreview = SyncPreview.PENDING
) {
    val pendingItems get() = if (sync == SyncPreview.SYNCED) 0 else 3
}

/** Settings/profiles are UI previews. They do not update the account or schedule alerts. */
@HiltViewModel
class SettingsPreviewViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsPreviewState(
        displayName = saved.get<String>("displayName") ?: "",
        milestonesEnabled = saved.get<Boolean>("milestones") ?: true,
        forecastsEnabled = saved.get<Boolean>("forecasts") ?: true,
        sync = SyncPreview.entries.firstOrNull { it.name == saved.get<String>("sync") } ?: SyncPreview.PENDING
    ))
    val uiState = mutableState.asStateFlow()

    fun saveName(name: String): String? {
        val trimmed = name.trim()
        val error = when {
            trimmed.length < 2 -> "Enter a name with at least 2 characters."
            trimmed.length > 60 -> "Keep your name to 60 characters or fewer."
            trimmed.any { it.isISOControl() } -> "Use a name without control characters."
            else -> null
        }
        if (error == null) update(mutableState.value.copy(displayName = trimmed))
        return error
    }
    fun setMilestones(enabled: Boolean) = update(mutableState.value.copy(milestonesEnabled = enabled))
    fun setForecasts(enabled: Boolean) = update(mutableState.value.copy(forecastsEnabled = enabled))
    fun selectSync(sync: SyncPreview) = update(mutableState.value.copy(sync = sync))
    fun previewSync() {
        if (mutableState.value.sync != SyncPreview.OFFLINE) selectSync(SyncPreview.SYNCED)
    }
    private fun update(state: SettingsPreviewState) {
        saved["displayName"] = state.displayName
        saved["milestones"] = state.milestonesEnabled
        saved["forecasts"] = state.forecastsEnabled
        saved["sync"] = state.sync.name
        mutableState.value = state
    }
}
