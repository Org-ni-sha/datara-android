package com.capstone.datara.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * A sign-in failure with a known Supabase-style error code as its message, so
 * [com.capstone.datara.ui.auth.AuthErrorParser] maps it like any other Auth error.
 */
class AuthFailure(code: String) : Exception(code)

class AuthRepository @Inject constructor(
    private val auth: Auth,
    private val functions: Functions
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

    /**
     * Signs in with a username through the `username-login` Edge Function, which resolves the
     * email server-side and returns only session tokens (see supabase/functions/username-login).
     */
    suspend fun signInWithUsername(username: String, password: String): Result<Unit> = runCatching {
        val response = try {
            functions.invoke(
                function = USERNAME_LOGIN_FUNCTION,
                body = buildJsonObject {
                    put("username", username)
                    put("password", password)
                }
            )
        } catch (e: RestException) {
            val details = "${e.error} ${e.description.orEmpty()} ${e.message.orEmpty()}"
            throw when {
                e.statusCode == 429 -> AuthFailure("over_request_rate_limit")
                "email_not_confirmed" in details -> AuthFailure("email_not_confirmed")
                e.statusCode == 400 -> AuthFailure("invalid_credentials")
                else -> e
            }
        }
        val session = sessionJson.decodeFromString<UserSession>(response.bodyAsText())
        auth.importSession(session)
        // The function returns tokens only; load the user so currentUserOrNull() is populated.
        auth.retrieveUserForCurrentSession(updateSession = true)
    }

    /**
     * Exchanges a Google ID token from Credential Manager for a Supabase session. [rawNonce] is
     * the unhashed nonce whose SHA-256 was sent to Google; Supabase hashes it to compare.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): Result<Unit> = runCatching {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
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

    private companion object {
        const val USERNAME_LOGIN_FUNCTION = "username-login"
        val sessionJson = Json { ignoreUnknownKeys = true }
    }
}