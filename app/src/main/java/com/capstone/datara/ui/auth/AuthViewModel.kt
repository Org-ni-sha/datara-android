package com.capstone.datara.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.datara.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    object Success : AuthUiState
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
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _passwordResetState = MutableStateFlow(PasswordResetState())
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()

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

    fun register(email: String, password: String, confirmPassword: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all required fields.")
            return
        }
        if (!AuthValidator.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (!AuthValidator.isPasswordValid(password)) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters long.")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = AuthUiState.Error("Passwords do not match. Please make sure they are identical.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signUp(trimmedEmail, password)
                .onSuccess { _uiState.value = AuthUiState.Success }
                .onFailure { _uiState.value = AuthUiState.Error(AuthErrorParser.parse(it, AuthAction.REGISTER)) }
        }
    }

    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both your email and password.")
            return
        }
        if (!AuthValidator.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signIn(trimmedEmail, password)
                .onSuccess { _uiState.value = AuthUiState.Success }
                .onFailure { _uiState.value = AuthUiState.Error(AuthErrorParser.parse(it, AuthAction.LOGIN)) }
        }
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