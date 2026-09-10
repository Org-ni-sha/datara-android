package com.capstone.datara.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthErrorParserTest {

    // --- Network Errors ---
    @Test
    fun parse_networkExceptions_returnsConnectivityMessage() {
        val expected = "Unable to connect to the server. Please check your internet connection."

        assertEquals(expected, AuthErrorParser.parse(UnknownHostException("Unable to resolve host"), AuthAction.LOGIN))
        assertEquals(expected, AuthErrorParser.parse(ConnectException("Failed to connect to /1.2.3.4:443"), AuthAction.REGISTER))
        assertEquals(expected, AuthErrorParser.parse(SocketTimeoutException("timeout"), AuthAction.SEND_RESET_CODE))
        assertEquals(expected, AuthErrorParser.parse(IOException("Failed to connect to host"), AuthAction.VERIFY_CODE))
    }

    // --- Login Errors ---
    @Test
    fun parse_login_invalidCredentials_returnsFriendlyMessage() {
        val error = RuntimeException("HTTP request to https://xyz.supabase.co/auth/v1/token?grant_type=password failed with message: Invalid login credentials")
        val message = AuthErrorParser.parse(error, AuthAction.LOGIN)

        assertEquals("Incorrect email or password. Please check your credentials and try again.", message)
    }

    @Test
    fun parse_login_unconfirmedEmail_returnsFriendlyMessage() {
        val error = RuntimeException("HTTP request failed: Email not confirmed")
        val message = AuthErrorParser.parse(error, AuthAction.LOGIN)

        assertEquals("Your email address has not been confirmed yet. Please check your inbox for the confirmation link.", message)
    }

    @Test
    fun parse_login_rateLimit_returnsFriendlyMessage() {
        val error = RuntimeException("Status code: 429 over_request_rate_limit")
        val message = AuthErrorParser.parse(error, AuthAction.LOGIN)

        assertEquals("Too many failed login attempts. Please wait a moment and try again.", message)
    }

    @Test
    fun parse_login_genericFallback_returnsFriendlyMessage() {
        val error = RuntimeException("Something internal went wrong")
        val message = AuthErrorParser.parse(error, AuthAction.LOGIN)

        assertEquals("Unable to log in. Please check your credentials and try again.", message)
    }

    // --- Register Errors ---
    @Test
    fun parse_register_alreadyRegistered_returnsFriendlyMessage() {
        val error = RuntimeException("HTTP request to https://xyz.supabase.co/auth/v1/signup failed with message: User already registered")
        val message = AuthErrorParser.parse(error, AuthAction.REGISTER)

        assertEquals("An account with this email already exists. Please log in instead.", message)
    }

    @Test
    fun parse_register_weakPassword_returnsFriendlyMessage() {
        val error = RuntimeException("Password should be at least 6 characters")
        val message = AuthErrorParser.parse(error, AuthAction.REGISTER)

        assertEquals("Password must be at least 6 characters long.", message)
    }

    @Test
    fun parse_register_rateLimit_returnsFriendlyMessage() {
        val error = RuntimeException("over_email_send_rate_limit: rate limit exceeded")
        val message = AuthErrorParser.parse(error, AuthAction.REGISTER)

        assertEquals("Too many signup attempts. Please wait a few minutes before trying again.", message)
    }

    // --- Password Reset Errors ---
    @Test
    fun parse_sendResetCode_rateLimit_returnsFriendlyMessage() {
        val error = RuntimeException("For security purposes, you can only request this once every 60 seconds")
        val message = AuthErrorParser.parse(error, AuthAction.SEND_RESET_CODE)

        assertEquals("Please wait a moment before requesting another verification code.", message)
    }

    @Test
    fun parse_verifyCode_invalidOrExpired_returnsFriendlyMessage() {
        val error = RuntimeException("Token has expired or is invalid")
        val message = AuthErrorParser.parse(error, AuthAction.VERIFY_CODE)

        assertEquals("The verification code is invalid or has expired. Please check the code or request a new one.", message)
    }

    @Test
    fun parse_updatePassword_samePassword_returnsFriendlyMessage() {
        val error = RuntimeException("New password should be different from the old password")
        val message = AuthErrorParser.parse(error, AuthAction.UPDATE_PASSWORD)

        assertEquals("New password cannot be the same as your previous password.", message)
    }

    @Test
    fun parse_updatePassword_sessionExpired_returnsFriendlyMessage() {
        val error = RuntimeException("invalid_jwt: session_expired")
        val message = AuthErrorParser.parse(error, AuthAction.UPDATE_PASSWORD)

        assertEquals("Your verification session has expired. Please request a new verification code.", message)
    }

    // --- Validation Checks ---
    @Test
    fun authValidator_validatesEmailsCorrectly() {
        assertTrue(AuthValidator.isValidEmail("test@example.com"))
        assertTrue(AuthValidator.isValidEmail("user.name+tag@sub.domain.ph"))
        assertFalse(AuthValidator.isValidEmail("plainaddress"))
        assertFalse(AuthValidator.isValidEmail("@missingusername.com"))
        assertFalse(AuthValidator.isValidEmail("user@.com"))
        assertFalse(AuthValidator.isValidEmail(""))
    }

    @Test
    fun authValidator_validatesPasswordLength() {
        assertTrue(AuthValidator.isPasswordValid("123456"))
        assertTrue(AuthValidator.isPasswordValid("securePassword123!"))
        assertFalse(AuthValidator.isPasswordValid("12345"))
        assertFalse(AuthValidator.isPasswordValid(""))
    }
}
