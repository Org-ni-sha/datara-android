package com.capstone.datara.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

import io.github.jan.supabase.auth.OtpType

class AuthRepository @Inject constructor(
    private val auth: Auth
) {
    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching { auth.signOut() }

    suspend fun resetPassword(email: String, redirectUrl: String? = null): Result<Unit> = runCatching {
        auth.resetPasswordForEmail(email = email, redirectUrl = redirectUrl)
    }

    suspend fun verifyResetCode(email: String, code: String): Result<Unit> = runCatching {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.updateUser {
            password = newPassword
        }
        auth.signOut()
    }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): Result<Unit> = runCatching {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
        auth.updateUser {
            password = newPassword
        }
        auth.signOut()
    }

    fun currentUserEmail(): String? = auth.currentUserOrNull()?.email
}