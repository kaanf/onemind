package com.onemind.backend.common.money

import java.util.Currency

/**
 * Exact money amount in integer minor units (haléř for CZK). Never a float (AGENTS.md rule 5).
 * Arithmetic only between amounts of the same currency.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency,
) {
    operator fun plus(other: Money): Money = Money(Math.addExact(amountMinor, sameCurrency(other).amountMinor), currency)

    operator fun minus(other: Money): Money = Money(Math.subtractExact(amountMinor, sameCurrency(other).amountMinor), currency)

    operator fun unaryMinus(): Money = Money(Math.negateExact(amountMinor), currency)

    private fun sameCurrency(other: Money): Money {
        require(other.currency == currency) { "Currency mismatch: $currency and ${other.currency}" }
        return other
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0, currency)
    }
}
