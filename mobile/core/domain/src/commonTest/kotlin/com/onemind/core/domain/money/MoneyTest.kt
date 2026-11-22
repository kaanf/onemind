package com.onemind.core.domain.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    private val eur = Currency("EUR")

    private fun czk(minor: Long) = Money(minor, Currency.CZK)

    @Test
    fun arithmeticIsExactInMinorUnits() {
        assertEquals(czk(4_832_050), czk(4_832_000) + czk(50))
        assertEquals(czk(-187_000), czk(0) - czk(187_000))
        assertEquals(czk(187_000), -czk(-187_000))
        assertTrue(czk(-1).isNegative)
        assertTrue(Money.zero(Currency.CZK).isZero)
    }

    @Test
    fun currenciesNeverMix() {
        assertFailsWith<IllegalArgumentException> { czk(100) + Money(100, eur) }
        assertFailsWith<IllegalArgumentException> { czk(100) < Money(100, eur) }
    }

    @Test
    fun overflowFailsInsteadOfWrapping() {
        assertFailsWith<IllegalStateException> { czk(Long.MAX_VALUE) + czk(1) }
        assertFailsWith<IllegalStateException> { czk(Long.MIN_VALUE) - czk(1) }
        assertFailsWith<IllegalStateException> { -czk(Long.MIN_VALUE) }
    }

    @Test
    fun sumAndOrdering() {
        assertEquals(czk(600), listOf(czk(100), czk(200), czk(300)).sum())
        assertNull(emptyList<Money>().sum())
        assertEquals(listOf(czk(-5), czk(0), czk(7)), listOf(czk(7), czk(-5), czk(0)).sorted())
    }

    @Test
    fun currencyCodesAreValidated() {
        assertEquals("CZK", Currency("CZK").code)
        listOf("czk", "CZ", "CZKK", "C2K", "").forEach { assertFailsWith<IllegalArgumentException>(it) { Currency(it) } }
    }
}
