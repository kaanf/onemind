package com.onemind.backend.pos.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/** POS money values (decimal strings, e.g. Storyous `"129.00"`) turned into exact minor units (rule 5). */
object PosMoney {
    /**
     * Minor units of [decimal] in [currency]. Fails on a value that isn't a decimal number or has more
     * fraction digits than the currency (no silent rounding).
     */
    fun minorUnits(
        decimal: String,
        currency: Currency,
    ): Long {
        val value = decimal.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Not a decimal money value: '$decimal'")
        val scaled =
            try {
                value.setScale(currency.defaultFractionDigits, RoundingMode.UNNECESSARY)
            } catch (e: ArithmeticException) {
                throw IllegalArgumentException("More fraction digits than ${currency.currencyCode} allows: '$decimal'", e)
            }
        return scaled.unscaledValue().longValueExact()
    }

    /**
     * Minor units of a POS **price** (menu product price, bill line unit price), rounded half-up when it
     * has more fraction digits than the currency (owner decision 2026-11-22). Only for prices: totals,
     * payments and discounts use the exact [minorUnits]. Fails on a value that isn't a decimal number.
     */
    fun minorUnitsRounded(
        decimal: String,
        currency: Currency,
    ): Long {
        val value = decimal.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Not a decimal money value: '$decimal'")
        return value.setScale(currency.defaultFractionDigits, RoundingMode.HALF_UP).unscaledValue().longValueExact()
    }

    /** Whether [decimal] has more fraction digits than [currency], so [minorUnitsRounded] rounds it. */
    fun needsRounding(
        decimal: String,
        currency: Currency,
    ): Boolean =
        (
            decimal
                .trim()
                .toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.scale() ?: 0
        ) > currency.defaultFractionDigits

    /** [minor] units of [currency] as a decimal string, the inverse of [minorUnits]. */
    fun decimal(
        minor: Long,
        currency: Currency,
    ): String = BigDecimal.valueOf(minor, currency.defaultFractionDigits).toPlainString()
}
