package com.onemind.core.domain.money

/**
 * An exact amount in integer minor units (haléř for CZK) with its currency, as the API sends it
 * (`{"amountMinor": 4832000, "currency": "CZK"}`, product rule 5). Never a `Float`/`Double`. Amounts of
 * different currencies never mix: arithmetic across currencies fails instead of guessing a rate.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency,
) : Comparable<Money> {
    val isNegative: Boolean get() = amountMinor < 0
    val isZero: Boolean get() = amountMinor == 0L

    operator fun plus(other: Money): Money = Money(addExact(amountMinor, other.sameCurrency().amountMinor), currency)

    operator fun minus(other: Money): Money = Money(addExact(amountMinor, negateExact(other.sameCurrency().amountMinor)), currency)

    operator fun unaryMinus(): Money = Money(negateExact(amountMinor), currency)

    override fun compareTo(other: Money): Int = amountMinor.compareTo(other.sameCurrency().amountMinor)

    private fun Money.sameCurrency(): Money {
        require(currency == this@Money.currency) { "Can't combine ${this@Money.currency} and $currency" }
        return this
    }

    companion object {
        fun zero(currency: Currency) = Money(0, currency)
    }
}

/** Overflow is an error, never a silent wrap-around (a wrong total is worse than a crash in a test). */
private fun addExact(
    a: Long,
    b: Long,
): Long {
    val result = a + b
    check((a xor result) and (b xor result) >= 0) { "Money overflow: $a + $b" }
    return result
}

private fun negateExact(a: Long): Long {
    check(a != Long.MIN_VALUE) { "Money overflow: -$a" }
    return -a
}

/** Sum of amounts in one currency; `null` for an empty list (there is no currency to give zero in). */
fun Iterable<Money>.sum(): Money? = reduceOrNull { total, next -> total + next }
