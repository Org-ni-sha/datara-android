package com.capstone.datara.ui.quota

import java.math.BigDecimal

/** Decimal units match the MB/GB labels on the form: 1 GB = 1,000 MB. */
enum class QuotaUnit(val bytes: Long) {
    MB(1_000_000L),
    GB(1_000_000_000L)
}

sealed interface QuotaAmountResult {
    data class Valid(val bytes: Long) : QuotaAmountResult
    data class Invalid(val message: String) : QuotaAmountResult
}

/** Form validation only; it neither creates a stored quota nor measures traffic. */
object QuotaInput {
    private val decimalAmount = Regex("(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)")
    private val maxBytes = BigDecimal.valueOf(Long.MAX_VALUE)
    private val minBytes = BigDecimal.valueOf(QuotaUnit.MB.bytes)

    fun parseAmount(amount: String, unit: QuotaUnit): QuotaAmountResult {
        val value = amount.trim()
        if (value.isEmpty()) return QuotaAmountResult.Invalid("Enter the amount you want to track.")
        if (value.length > 32 || !decimalAmount.matches(value)) {
            return QuotaAmountResult.Invalid("Enter a positive number, such as 10 or 1.5.")
        }

        // A comma with three trailing digits can also mean a thousands separator.
        // Ask for an explicit decimal point instead of silently choosing a different quota.
        val comma = value.indexOf(',')
        if (comma > 0 && value.length - comma - 1 == 3 &&
            value.substring(0, comma).any { it != '0' }
        ) {
            return QuotaAmountResult.Invalid("Use a decimal point for this amount, without thousands separators.")
        }

        val bytes = value.replace(',', '.').toBigDecimal().multiply(BigDecimal.valueOf(unit.bytes))
        if (bytes < minBytes) return QuotaAmountResult.Invalid("Choose at least 1 MB (0.001 GB).")
        if (bytes > maxBytes) return QuotaAmountResult.Invalid("This amount is too large. Enter a smaller quota.")

        return try {
            QuotaAmountResult.Valid(bytes.longValueExact())
        } catch (_: ArithmeticException) {
            QuotaAmountResult.Invalid("Use fewer decimal places for this amount.")
        }
    }
}
