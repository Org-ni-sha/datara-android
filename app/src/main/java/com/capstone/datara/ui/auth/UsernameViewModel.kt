package com.capstone.datara.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.AuthRepository
import com.capstone.datara.data.repository.ProfileRepository
import com.capstone.datara.data.repository.UsernameTakenException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UsernameAvailability { Unknown, Checking, Available, Taken }

data class UsernameUiState(
    val input: String = "",
    /** Format problem with [input], shown under the field; null when the format is fine. */
    val formatError: String? = null,
    val availability: UsernameAvailability = UsernameAvailability.Unknown,
    val isSaving: Boolean = false,
    val isSigningOut: Boolean = false,
    /** Failure of the last save or sign-out, shown in the error banner. */
    val error: String? = null,
    val isSaved: Boolean = false
) {
    val canSubmit: Boolean
        get() = input.isNotBlank() && formatError == null &&
            availability != UsernameAvailability.Taken && !isSaving && !isSigningOut
}

/**
 * Backs the username screen shown after sign-up, and at launch for any signed-in user without a
 * username (see [com.capstone.datara.ui.session.SessionState.NeedsUsername]).
 *
 * Availability is checked as the user types, after a short pause. The check is advisory: the
 * unique index decides on save, so a name taken in between still reports as taken.
 */
@HiltViewModel
class UsernameViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsernameUiState())
    val uiState: StateFlow<UsernameUiState> = _uiState.asStateFlow()

    private var availabilityJob: Job? = null

    fun onInputChange(input: String) {
        val formatError = if (input.isBlank()) null else AuthValidator.usernameError(input)
        _uiState.update {
            it.copy(
                input = input,
                formatError = formatError,
                availability = UsernameAvailability.Unknown,
                error = null
            )
        }
        availabilityJob?.cancel()
        if (input.isBlank() || formatError != null) return

        val candidate = AuthValidator.canonicalUsername(input)
        availabilityJob = viewModelScope.launch {
            delay(AVAILABILITY_DEBOUNCE_MS)
            _uiState.update { it.copy(availability = UsernameAvailability.Checking) }
            val availability = profileRepository.isUsernameAvailable(candidate)
                .map { available ->
                    if (available) UsernameAvailability.Available else UsernameAvailability.Taken
                }
                // Offline or failed check: say nothing and let the save decide.
                .getOrDefault(UsernameAvailability.Unknown)
            _uiState.update { it.copy(availability = availability) }
        }
    }

    fun submit() {
        val state = _uiState.value
        val formatError = AuthValidator.usernameError(state.input)
        if (formatError != null) {
            _uiState.update { it.copy(formatError = formatError) }
            return
        }
        if (!state.canSubmit) return

        availabilityJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            profileRepository.setUsername(AuthValidator.canonicalUsername(state.input))
                .onSuccess { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { failure ->
                    _uiState.update {
                        if (failure is UsernameTakenException) {
                            it.copy(isSaving = false, availability = UsernameAvailability.Taken)
                        } else {
                            it.copy(
                                isSaving = false,
                                error = "Couldn't save your username. Check your connection and try again."
                            )
                        }
                    }
                }
        }
    }

    /** Lets a user who signed in with the wrong account leave this screen. */
    fun signOut(onComplete: () -> Unit) {
        if (_uiState.value.isSigningOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningOut = true, error = null) }
            authRepository.signOut()
                .onSuccess {
                    _uiState.update { it.copy(isSigningOut = false) }
                    onComplete()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSigningOut = false,
                            error = "Couldn't sign out. Check your connection and try again."
                        )
                    }
                }
        }
    }

    private companion object {
        const val AVAILABILITY_DEBOUNCE_MS = 400L
    }
}
