package com.capstone.datara.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SyncPreview(val label: String) { PENDING("Pending"), SYNCED("Synced"), OFFLINE("Offline"), ERROR("Error") }
data class SettingsPreviewState(
    val sync: SyncPreview = SyncPreview.PENDING
) {
    val pendingItems get() = if (sync == SyncPreview.SYNCED) 0 else 3
}

/**
 * The simulated cloud-sync states (C3 pending). Notification preferences are real and live in
 * [NotificationPreferencesViewModel]; the profile name in [com.capstone.datara.ui.profile.ProfileViewModel].
 */
@HiltViewModel
class SettingsPreviewViewModel @Inject constructor(private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsPreviewState(
        sync = SyncPreview.entries.firstOrNull { it.name == saved.get<String>("sync") } ?: SyncPreview.PENDING
    ))
    val uiState = mutableState.asStateFlow()

    fun selectSync(sync: SyncPreview) = update(mutableState.value.copy(sync = sync))
    fun previewSync() {
        if (mutableState.value.sync != SyncPreview.OFFLINE) selectSync(SyncPreview.SYNCED)
    }
    private fun update(state: SettingsPreviewState) {
        saved["sync"] = state.sync.name
        mutableState.value = state
    }
}
