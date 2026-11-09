package com.capstone.datara.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    // --- isValidName ---------------------------------------------------------

    @Test
    fun `isValidName rejects blank and single character names`() {
        assertFalse(AuthValidator.isValidName(""))
        assertFalse(AuthValidator.isValidName("   "))
        assertFalse(AuthValidator.isValidName("J"))
    }

    @Test
    fun `isValidName ignores surrounding whitespace`() {
        assertFalse(AuthValidator.isValidName("  J  "))
        assertTrue(AuthValidator.isValidName("  Jo  "))
    }

    @Test
    fun `isValidName accepts an ordinary full name`() {
        assertTrue(AuthValidator.isValidName("Juan Dela Cruz"))
    }

    // --- validateRegistration ------------------------------------------------

    @Test
    fun `validateRegistration rejects a username with spaces`() {
        assertEquals(
            "Use only letters, numbers and underscores.",
            AuthValidator.validateRegistration(username = "juan dc", email = "juan@ustp.edu.ph", password = "secret123")
        )
    }

    @Test
    fun `validateRegistration returns null when every field is valid`() {
        assertNull(
            AuthValidator.validateRegistration(
                username = "juan_dc",
                email = "juan@ustp.edu.ph",
                password = "secret123"
            )
        )
    }

    @Test
    fun `validateRegistration reports empty fields before anything else`() {
        // Email is also malformed here; the blank-field message must win so the user is told
        // to fill the form rather than being shown a format complaint about an empty box.
        assertEquals(
            "Please fill in all required fields.",
            AuthValidator.validateRegistration(
                username = "",
                email = "not-an-email",
                password = ""
            )
        )
    }

    @Test
    fun `validateRegistration rejects a too-short username`() {
        assertEquals(
            "Username must be at least 3 characters.",
            AuthValidator.validateRegistration(
                username = "jd",
                email = "juan@ustp.edu.ph",
                password = "secret123"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a malformed email`() {
        assertEquals(
            "Please enter a valid email address.",
            AuthValidator.validateRegistration(
                username = "juan_dc",
                email = "juan@ustp",
                password = "secret123"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a password under eight characters`() {
        assertEquals(
            AuthValidator.PASSWORD_RULE_MESSAGE,
            AuthValidator.validateRegistration(
                username = "juan_dc",
                email = "juan@ustp.edu.ph",
                password = "abc1234"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a long password without a number`() {
        assertEquals(
            AuthValidator.PASSWORD_RULE_MESSAGE,
            AuthValidator.validateRegistration(
                username = "juan_dc",
                email = "juan@ustp.edu.ph",
                password = "abcdefghij"
            )
        )
    }

    // --- passwordStrength ----------------------------------------------------

    @Test
    fun `passwordStrength is null for an empty field and weak below the rule`() {
        assertNull(AuthValidator.passwordStrength(""))
        assertEquals(PasswordStrength.Weak, AuthValidator.passwordStrength("abc123"))
        assertEquals(PasswordStrength.Weak, AuthValidator.passwordStrength("12345678"))
    }

    @Test
    fun `passwordStrength rates longer mixed passwords higher`() {
        assertEquals(PasswordStrength.Fair, AuthValidator.passwordStrength("secret123"))
        assertEquals(PasswordStrength.Strong, AuthValidator.passwordStrength("Secret-Quota-2026"))
    }
}
