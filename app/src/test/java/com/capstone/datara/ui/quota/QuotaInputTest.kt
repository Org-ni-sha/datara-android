package com.capstone.datara.ui.quota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaInputTest {
    @Test
    fun decimalUnitsConvertWithoutFloatingPointRounding() {
        assertEquals(QuotaAmountResult.Valid(1_500_000_000L), QuotaInput.parseAmount("1.5", QuotaUnit.GB))
        assertEquals(QuotaAmountResult.Valid(250_000_000L), QuotaInput.parseAmount("250", QuotaUnit.MB))
        assertEquals(QuotaAmountResult.Valid(1_234_567L), QuotaInput.parseAmount("1.234567", QuotaUnit.MB))
    }

    @Test
    fun acceptsWhitespaceAndUnambiguousDecimalComma() {
        assertEquals(QuotaAmountResult.Valid(1_500_000_000L), QuotaInput.parseAmount(" 1,5 ", QuotaUnit.GB))
        assertEquals(QuotaAmountResult.Valid(1_000_000L), QuotaInput.parseAmount("0,001", QuotaUnit.GB))
    }

    @Test
    fun acceptsExactMinimumAndRejectsSmallerAmounts() {
        assertEquals(QuotaAmountResult.Valid(1_000_000L), QuotaInput.parseAmount("1", QuotaUnit.MB))
        assertEquals(QuotaAmountResult.Valid(1_000_000L), QuotaInput.parseAmount("0.001", QuotaUnit.GB))
        assertInvalid("0.999999", QuotaUnit.MB)
        assertInvalid("0.000999", QuotaUnit.GB)
        assertInvalid("0", QuotaUnit.GB)
    }

    @Test
    fun rejectsNonFiniteScientificSignedAndMalformedAmounts() {
        listOf("", " ", "NaN", "Infinity", "1e3", "-1", "+1", "1..5", "1,2.3", "1 000", "1,000")
            .forEach { assertInvalid(it, QuotaUnit.GB) }
    }

    @Test
    fun rejectsOverflowAndFractionalBytes() {
        assertEquals(
            QuotaAmountResult.Valid(Long.MAX_VALUE),
            QuotaInput.parseAmount("9223372036.854775807", QuotaUnit.GB)
        )
        assertInvalid("9223372036.854775808", QuotaUnit.GB)
        assertInvalid("9".repeat(100), QuotaUnit.GB)
        assertInvalid("1.0000001", QuotaUnit.MB)
    }

    private fun assertInvalid(amount: String, unit: QuotaUnit) {
        assertTrue("Expected invalid amount: $amount $unit", QuotaInput.parseAmount(amount, unit) is QuotaAmountResult.Invalid)
    }
}
