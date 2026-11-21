package com.onemind.backend.common.money.infra.persistence

import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrencyConverterTest {
    private val converter = CurrencyConverter()

    @Test
    fun `converts char(3) codes both ways`() {
        assertEquals(Currency.getInstance("CZK"), converter.from("CZK"))
        assertEquals("EUR", converter.to(Currency.getInstance("EUR")))
    }

    @Test
    fun `keeps null`() {
        assertNull(converter.from(null))
        assertNull(converter.to(null))
    }
}
