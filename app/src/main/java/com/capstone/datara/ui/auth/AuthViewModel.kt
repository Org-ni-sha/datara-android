package com.capstone.datara.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.AuthRepository
import com.capstone.datara.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    /** Signed in. [needsUsername] sends the user to the username screen instead of the dashboard. */
    data class Success(val needsUsername: Boolean) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

enum class ResetPasswordStep {
    ENTER_EMAIL,
    ENTER_CODE,
    SET_NEW_PASSWORD,
    SUCCESS
}

data class PasswordResetState(
    val step: ResetPasswordStep = ResetPasswordStep.ENTER_EMAIL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val email: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _passwordResetState = MutableStateFlow(PasswordResetState())
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

    /** Email of the signed-in user, or null when there is no session. */
    val currentUserEmail: String?
        get() = repository.currentUserEmail()

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    fun clearResetError() {
        if (_passwordResetState.value.error != null) {
            _passwordResetState.value = _passwordResetState.value.copy(error = null)
        }
    }

    fun register(name: String, email: String, password: String, confirmPassword: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        AuthValidator.validateRegistration(trimmedName, trimmedEmail, password, confirmPassword)
            ?.let { error ->
                _uiState.value = AuthUiState.Error(error)
                return
            }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signUp(trimmedEmail, password, trimmedName)
                // A brand-new account never has a username yet.
                .onSuccess { _uiState.value = AuthUiState.Success(needsUsername = true) }
                .onFailure { _uiState.value = AuthUiState.Error(AuthErrorParser.parse(it, AuthAction.REGISTER)) }
        }
    }

    /**
     * Signs out and invokes [onComplete] once Supabase has cleared the persisted session, so the
     * caller navigates only after auto-login can no longer pick the session back up on relaunch.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            if (_uiState.value is AuthUiState.Loading) return@launch
            _uiState.value = AuthUiState.Loading
            repository.signOut()
                .onSuccess {
                    _uiState.value = AuthUiState.Idle
                    onComplete()
                }
                .onFailure {
                    _uiState.value = AuthUiState.Error("Couldn't sign out. Check your connection and try again.")
                }
        }
    }

    /** Signs in with an email or a username in [identifier]; see [AuthValidator.isEmailIdentifier]. */
    fun login(identifier: String, password: String) {
        val trimmed = identifier.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email or username and your password.")
            return
        }
        val isEmail = AuthValidator.isEmailIdentifier(trimmed)
        if (isEmail && !AuthValidator.isValidEmail(trimmed)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = if (isEmail) {
                repository.signIn(trimmed, password)
            } else {
                // A malformed username can't exist, but it gets the same answer as a wrong
                // password rather than a hint about which part was wrong.
                repository.signInWithUsername(AuthValidator.canonicalUsername(trimmed), password)
            }
            result
                .onSuccess { completeSignIn() }
                .onFailure { _uiState.value = AuthUiState.Error(AuthErrorParser.parse(it, AuthAction.LOGIN)) }
        }
    }

    /** Finishes Google sign-in (or sign-up) with an ID token from [requestGoogleIdToken]. */
    fun signInWithGoogle(token: GoogleIdToken) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signInWithGoogle(token.idToken, token.rawNonce)
                .onSuccess { completeSignIn() }
                .onFailure { _uiState.value = AuthUiState.Error(AuthErrorParser.parse(it, AuthAction.LOGIN)) }
        }
    }

    /** Shows a Credential Manager failure that isn't a user cancellation. */
    fun showError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    /**
     * Decides where a fresh sign-in goes. If the profile can't be read, the user goes to the
     * dashboard and the launch gate asks for a username next time (see SessionViewModel).
     */
    private suspend fun completeSignIn() {
        val needsUsername = profileRepository.fetchUsername()
            .map { it == null }
            .getOrDefault(false)
        _uiState.value = AuthUiState.Success(needsUsername)
    }

    fun sendResetCode(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _passwordResetState.value = _passwordResetState.value.copy(
                error = "Please enter your email address."
            )
            return
        }
        if (!AuthValidator.isValidEmail(trimmedEmail)) {
            _passwordResetState.value = _passwordResetState.value.copy(
                error = "Please enter a valid email address."
            )
            return
        }
        viewModelScope.launch {
            _passwordResetState.value = _passwordResetState.value.copy(isLoading = true, error = null)
            repository.resetPassword(trimmedEmail)
                .onSuccess {
                    _passwordResetState.value = PasswordResetState(
                        step = ResetPasswordStep.ENTER_CODE,
                        isLoading = false,
                        error = null,
                        email = trimmedEmail
                    )
                }
                .onFailure {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        isLoading = false,
                        error = AuthErrorParser.parse(it, AuthAction.SEND_RESET_CODE)
                    )
                }
        }
    }

    fun verifyResetCode(code: String) {
        val trimmedCode = code.trim()
        if (trimmedCode.length != 6) {
            _passwordResetState.value = _passwordResetState.value.copy(
                error = "Please enter the complete 6-digit verification code."
            )
            return
        }

        viewModelScope.launch {
            _passwordResetState.value = _passwordResetState.value.copy(isLoading = true, error = null)
            repository.verifyResetCode(
                email = _passwordResetState.value.email,
                code = trimmedCode
            )
                .onSuccess {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        step = ResetPasswordStep.SET_NEW_PASSWORD,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        isLoading = false,
                        error = AuthErrorParser.parse(it, AuthAction.VERIFY_CODE)
                    )
                }
        }
    }

    fun updateNewPassword(newPassword: String, confirmPassword: String) {
        if (!AuthValidator.isPasswordValid(newPassword)) {
            _passwordResetState.value = _passwordResetState.value.copy(
                error = "Password must be at least 6 characters long."
            )
            return
        }
        if (newPassword != confirmPassword) {
            _passwordResetState.value = _passwordResetState.value.copy(
                error = "Passwords do not match. Please make sure they are identical."
            )
            return
        }

        viewModelScope.launch {
            _passwordResetState.value = _passwordResetState.value.copy(isLoading = true, error = null)
            repository.updatePassword(newPassword)
                .onSuccess {
                    _passwordResetState.value = PasswordResetState(
                        step = ResetPasswordStep.SUCCESS,
                        isLoading = false,
                        error = null,
                        email = _passwordResetState.value.email
                    )
                }
                .onFailure {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        isLoading = false,
                        error = AuthErrorParser.parse(it, AuthAction.UPDATE_PASSWORD)
                    )
                }
        }
    }

    fun resendResetCode() {
        val email = _passwordResetState.value.email
        if (email.isBlank()) return
        viewModelScope.launch {
            _passwordResetState.value = _passwordResetState.value.copy(isLoading = true, error = null)
            repository.resetPassword(email)
                .onSuccess {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure {
                    _passwordResetState.value = _passwordResetState.value.copy(
                        isLoading = false,
                        error = AuthErrorParser.parse(it, AuthAction.RESEND_CODE)
                    )
                }
        }
    }

    fun backToEmailStep() {
        _passwordResetState.value = PasswordResetState(
            step = ResetPasswordStep.ENTER_EMAIL,
            email = _passwordResetState.value.email
        )
    }

    fun backToCodeStep() {
        _passwordResetState.value = _passwordResetState.value.copy(
            step = ResetPasswordStep.ENTER_CODE,
            error = null
        )
    }

    fun clearPasswordResetState() {
        _passwordResetState.value = PasswordResetState()
    }
}