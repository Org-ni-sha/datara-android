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
    fun `validateRegistration returns null when every field is valid`() {
        assertNull(
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp.edu.ph",
                password = "secret123",
                confirmPassword = "secret123"
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
                name = "",
                email = "not-an-email",
                password = "",
                confirmPassword = ""
            )
        )
    }

    @Test
    fun `validateRegistration rejects a too-short name`() {
        assertEquals(
            "Please enter your full name.",
            AuthValidator.validateRegistration(
                name = "J",
                email = "juan@ustp.edu.ph",
                password = "secret123",
                confirmPassword = "secret123"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a malformed email`() {
        assertEquals(
            "Please enter a valid email address.",
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp",
                password = "secret123",
                confirmPassword = "secret123"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a password under eight characters`() {
        assertEquals(
            AuthValidator.PASSWORD_RULE_MESSAGE,
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp.edu.ph",
                password = "abc1234",
                confirmPassword = "abc1234"
            )
        )
    }

    @Test
    fun `validateRegistration rejects a long password without a number`() {
        assertEquals(
            AuthValidator.PASSWORD_RULE_MESSAGE,
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp.edu.ph",
                password = "abcdefghij",
                confirmPassword = "abcdefghij"
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

    @Test
    fun `validateRegistration rejects mismatched passwords`() {
        assertEquals(
            "Passwords do not match. Please make sure they are identical.",
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp.edu.ph",
                password = "secret123",
                confirmPassword = "secret124"
            )
        )
    }

    @Test
    fun `validateRegistration checks password length before comparing the two passwords`() {
        // Both rules fail at once. Length is the more actionable message, so it must come first.
        assertEquals(
            AuthValidator.PASSWORD_RULE_MESSAGE,
            AuthValidator.validateRegistration(
                name = "Juan Dela Cruz",
                email = "juan@ustp.edu.ph",
                password = "abc",
                confirmPassword = "xyz"
            )
        )
    }
}
