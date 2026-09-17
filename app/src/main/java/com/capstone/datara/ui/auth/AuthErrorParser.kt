package com.capstone.datara.ui.auth

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class AuthAction {
    LOGIN,
    REGISTER,
    SEND_RESET_CODE,
    VERIFY_CODE,
    UPDATE_PASSWORD,
    RESEND_CODE
}

object AuthErrorParser {

    /**
     * Translates a technical [Throwable] (e.g. Supabase RestException, HttpRequestException,
     * network socket exceptions) into a clean, human-readable user-facing message.
     */
    fun parse(throwable: Throwable, action: AuthAction): String {
        // 1. Check for network / connectivity issues
        if (isNetworkError(throwable)) {
            return "Unable to connect to the server. Please check your internet connection."
        }

        // 2. Extract technical error clues (message, RestException error/description, cause)
        val rawMessage = throwable.message.orEmpty()
        val restError = (throwable as? RestException)?.error.orEmpty()
        val restDescription = (throwable as? RestException)?.description.orEmpty()
        val combinedDetails = "$rawMessage $restError $restDescription ${throwable.cause?.message.orEmpty()}".lowercase()

        // 3. Map to specific user-friendly messages based on action and error clues
        return when (action) {
            AuthAction.LOGIN -> mapLoginError(combinedDetails)
            AuthAction.REGISTER -> mapRegisterError(combinedDetails)
            AuthAction.SEND_RESET_CODE -> mapSendResetCodeError(combinedDetails)
            AuthAction.VERIFY_CODE -> mapVerifyCodeError(combinedDetails)
            AuthAction.UPDATE_PASSWORD -> mapUpdatePasswordError(combinedDetails)
            AuthAction.RESEND_CODE -> mapResendCodeError(combinedDetails)
        }
    }

    private fun isNetworkError(throwable: Throwable): Boolean {
        if (throwable is UnknownHostException ||
            throwable is ConnectException ||
            throwable is SocketTimeoutException ||
            throwable is HttpRequestException
        ) {
            return true
        }

        val msg = (throwable.message.orEmpty() + " " + throwable.cause?.message.orEmpty()).lowercase()
        return msg.contains("failed to connect") ||
                msg.contains("unable to resolve host") ||
                msg.contains("connection refused") ||
                msg.contains("network is unreachable") ||
                msg.contains("timed out") ||
                msg.contains("connecttimeoutexception") ||
                msg.contains("sockettimeoutexception")
    }

    private fun mapLoginError(details: String): String {
        return when {
            details.contains("invalid login credentials") ||
                    details.contains("invalid_grant") ||
                    details.contains("invalid_credentials") ||
                    details.contains("invalid email or password") -> {
                "Incorrect email or password. Please check your credentials and try again."
            }
            details.contains("email not confirmed") ||
                    details.contains("email_not_confirmed") -> {
                "Your email address has not been confirmed yet. Please check your inbox for the confirmation link."
            }
            details.contains("user not found") ||
                    details.contains("user_not_found") -> {
                "No account found with this email. Please check your email or sign up."
            }
            details.contains("over_request_rate_limit") ||
                    details.contains("too many requests") ||
                    details.contains("rate limit") ||
                    details.contains("status code: 429") ||
                    details.contains("status code 429") -> {
                "Too many failed login attempts. Please wait a moment and try again."
            }
            else -> "Unable to log in. Please check your credentials and try again."
        }
    }

    private fun mapRegisterError(details: String): String {
        return when {
            details.contains("user already registered") ||
                    details.contains("user_already_exists") ||
                    details.contains("email already in use") ||
                    details.contains("already registered") -> {
                "An account with this email already exists. Please log in instead."
            }
            details.contains("password should be at least") ||
                    details.contains("weak_password") ||
                    details.contains("signup requires a valid password") -> {
                "Password must be at least 6 characters long."
            }
            details.contains("unable to validate email address") ||
                    details.contains("invalid_email") ||
                    details.contains("invalid email") -> {
                "Please enter a valid email address."
            }
            details.contains("over_email_send_rate_limit") ||
                    details.contains("too many requests") ||
                    details.contains("rate limit") ||
                    details.contains("status code: 429") ||
                    details.contains("status code 429") -> {
                "Too many signup attempts. Please wait a few minutes before trying again."
            }
            else -> "Unable to create account. Please try again."
        }
    }

    private fun mapSendResetCodeError(details: String): String {
        return when {
            details.contains("for security purposes") ||
                    details.contains("over_email_send_rate_limit") ||
                    details.contains("rate limit") ||
                    details.contains("status code: 429") ||
                    details.contains("status code 429") -> {
                "Please wait a moment before requesting another verification code."
            }
            details.contains("user not found") ||
                    details.contains("user_not_found") -> {
                "No account found with this email address."
            }
            details.contains("unable to validate email address") ||
                    details.contains("invalid email") -> {
                "Please enter a valid email address."
            }
            else -> "Unable to send verification code. Please try again."
        }
    }

    private fun mapVerifyCodeError(details: String): String {
        return when {
            details.contains("token has expired or is invalid") ||
                    details.contains("invalid_grant") ||
                    details.contains("otp_expired") ||
                    details.contains("token is invalid") ||
                    details.contains("invalid token") ||
                    details.contains("expired") -> {
                "The verification code is invalid or has expired. Please check the code or request a new one."
            }
            details.contains("too many requests") ||
                    details.contains("rate limit") ||
                    details.contains("status code: 429") ||
                    details.contains("status code 429") -> {
                "Too many verification attempts. Please wait a moment and try again."
            }
            else -> "Failed to verify code. Please try again."
        }
    }

    private fun mapUpdatePasswordError(details: String): String {
        return when {
            details.contains("same_password") ||
                    details.contains("new password should be different") -> {
                "New password cannot be the same as your previous password."
            }
            details.contains("password should be at least") ||
                    details.contains("weak_password") -> {
                "Password must be at least 6 characters long."
            }
            details.contains("invalid_jwt") ||
                    details.contains("session_expired") ||
                    details.contains("not logged in") ||
                    details.contains("user from sub claim") -> {
                "Your verification session has expired. Please request a new verification code."
            }
            else -> "Failed to update password. Please try again."
        }
    }

    private fun mapResendCodeError(details: String): String {
        return when {
            details.contains("for security purposes") ||
                    details.contains("over_email_send_rate_limit") ||
                    details.contains("rate limit") ||
                    details.contains("status code: 429") ||
                    details.contains("status code 429") -> {
                "Please wait a moment before requesting another verification code."
            }
            else -> "Failed to resend verification code. Please try again."
        }
    }
}

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.isNotEmpty() && EMAIL_REGEX.matches(trimmed)
    }

    fun isPasswordValid(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        return name.trim().length >= 2
    }

    /**
     * Validates the whole registration form and returns the first error message, or null when
     * the form is valid. Kept pure and free of coroutines so it can be unit tested directly;
     * [AuthViewModel.register] delegates to it rather than duplicating the rules.
     */
    fun validateRegistration(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): String? = when {
        name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
            "Please fill in all required fields."

        !isValidName(name) ->
            "Please enter your full name."

        !isValidEmail(email) ->
            "Please enter a valid email address."

        !isPasswordValid(password) ->
            "Password must be at least 6 characters long."

        password != confirmPassword ->
            "Passwords do not match. Please make sure they are identical."

        else -> null
    }
}
