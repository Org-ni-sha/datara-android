package com.capstone.datara.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayNameValidatorTest {

    @Test
    fun `accepts an ordinary name and ignores surrounding whitespace`() {
        assertNull(DisplayNameValidator.error("  Juan Dela Cruz  "))
        assertNull(DisplayNameValidator.error("Jo"))
        assertNull(DisplayNameValidator.error("a".repeat(60)))
    }

    @Test
    fun `rejects names that are too short, too long or contain control characters`() {
        listOf("", "   ", "x", "a".repeat(61), "Hi\nthere").forEach {
            assertNotNull(it, DisplayNameValidator.error(it))
        }
        assertEquals("Enter a name with at least 2 characters.", DisplayNameValidator.error(" x "))
    }
}
