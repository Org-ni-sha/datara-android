package com.capstone.datara.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    // --- canonicalUsername ---------------------------------------------------

    @Test
    fun `canonicalUsername trims and lowercases`() {
        assertEquals("juan_dc", AuthValidator.canonicalUsername("  Juan_DC "))
    }

    // --- isValidUsername / usernameError -------------------------------------

    @Test
    fun `accepts letters numbers and underscores within length limits`() {
        assertTrue(AuthValidator.isValidUsername("abc"))
        assertTrue(AuthValidator.isValidUsername("juan_dc_2026"))
        assertTrue(AuthValidator.isValidUsername("a".repeat(20)))
        assertNull(AuthValidator.usernameError("Juan_DC"))
    }

    @Test
    fun `rejects names that are too short or too long`() {
        assertFalse(AuthValidator.isValidUsername("ab"))
        assertFalse(AuthValidator.isValidUsername("a".repeat(21)))
        assertEquals("Username must be at least 3 characters.", AuthValidator.usernameError("ab"))
        assertEquals("Username can be at most 20 characters.", AuthValidator.usernameError("a".repeat(21)))
    }

    @Test
    fun `rejects spaces, dots and symbols`() {
        listOf("juan dc", "juan.dc", "juan-dc", "juan@dc", "jüan").forEach {
            assertFalse(it, AuthValidator.isValidUsername(it))
            assertEquals("Use only letters, numbers and underscores.", AuthValidator.usernameError(it))
        }
    }

    @Test
    fun `blank input asks for a username`() {
        assertEquals("Please choose a username.", AuthValidator.usernameError("   "))
    }

    // --- isEmailIdentifier ---------------------------------------------------

    @Test
    fun `login identifier with an at sign is an email`() {
        assertTrue(AuthValidator.isEmailIdentifier("juan@ustp.edu.ph"))
        assertFalse(AuthValidator.isEmailIdentifier("juan_dc"))
    }
}
