package com.onemind.backend.common.money

import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    private val czk = Currency.getInstance("CZK")
    private val eur = Currency.getInstance("EUR")

    @Test
    fun `adds and subtracts minor units exactly`() {
        assertEquals(Money(4_832_050, czk), Money(4_832_000, czk) + Money(50, czk))
        assertEquals(Money(-50, czk), Money.zero(czk) - Money(50, czk))
        assertEquals(Money(-50, czk), -Money(50, czk))
    }

    @Test
    fun `refuses to mix currencies`() {
        assertFailsWith<IllegalArgumentException> { Money(100, czk) + Money(100, eur) }
    }

    @Test
    fun `fails on overflow instead of wrapping`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, czk) + Money(1, czk) }
    }
}
