package com.capstone.datara.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

import io.github.jan.supabase.auth.OtpType

class AuthRepository @Inject constructor(
    private val auth: Auth
) {
    val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    /**
     * Signs the user up and passes [name] as auth metadata.
     *
     * The profile row in `public.users` is created by the `on_auth_user_created` trigger
     * (see supabase/migrations/20260917000003_handle_new_user.sql), which reads
     * `raw_user_meta_data ->> 'name'`. Sending the name here means the row is complete the
     * moment it is created, with no follow-up insert that could be lost to a crash or a
     * dropped connection between sign-up and write.
     */
    suspend fun signUp(email: String, password: String, name: String): Result<Unit> = runCatching {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject {
                put("name", name)
            }
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