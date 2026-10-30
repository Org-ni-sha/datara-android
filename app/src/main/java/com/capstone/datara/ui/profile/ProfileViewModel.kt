package com.capstone.datara.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Display-name rules shared by the Profile screen; pure so they can be unit tested. */
object DisplayNameValidator {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 60

    /** Returns why [name] can't be saved, or null when it can. Callers save the trimmed form. */
    fun error(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.length < MIN_LENGTH -> "Enter a name with at least $MIN_LENGTH characters."
            trimmed.length > MAX_LENGTH -> "Keep your name to $MAX_LENGTH characters or fewer."
            trimmed.any { it.isISOControl() } -> "Use a name without control characters."
            else -> null
        }
    }
}

data class ProfileUiState(
    /** `users.name` — set at sign-up (or from Google) and editable on the Profile screen. */
    val name: String = "",
    val username: String? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    /** True after the last save succeeded; cleared when the user edits the name again. */
    val nameSaved: Boolean = false
)

/**
 * The signed-in user's real profile (name and username) from `public.users`. Unlike the other
 * Settings previews, name edits here are saved to the account.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            profileRepository.fetchProfile()
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            name = profile.name.orEmpty(),
                            username = profile.username,
                            isLoading = false
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    /**
     * Validates [name] immediately and returns the problem, or null and saves in the background.
     * The save's outcome arrives through [uiState] ([ProfileUiState.nameSaved] or
     * [ProfileUiState.saveError]).
     */
    fun saveName(name: String): String? {
        DisplayNameValidator.error(name)?.let { return it }
        if (_uiState.value.isSaving) return null

        val trimmed = name.trim()
        _uiState.update { it.copy(isSaving = true, saveError = null, nameSaved = false) }
        viewModelScope.launch {
            profileRepository.updateName(trimmed)
                .onSuccess {
                    _uiState.update { it.copy(name = trimmed, isSaving = false, nameSaved = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = "Couldn't save your name. Check your connection and try again."
                        )
                    }
                }
        }
        return null
    }

    fun onNameEdited() {
        if (_uiState.value.nameSaved || _uiState.value.saveError != null) {
            _uiState.update { it.copy(nameSaved = false, saveError = null) }
        }
    }
}
