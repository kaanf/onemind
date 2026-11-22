package com.onemind.backend.pos

import com.onemind.backend.pos.domain.PosMoney
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PosMoneyTest {
    private val czk = Currency.getInstance("CZK")
    private val jpy = Currency.getInstance("JPY")

    @Test
    fun `decimal strings become exact minor units`() {
        assertEquals(12900, PosMoney.minorUnits("129.00", czk))
        assertEquals(12950, PosMoney.minorUnits("129.5", czk))
        assertEquals(-1050, PosMoney.minorUnits("-10.50", czk))
        assertEquals(0, PosMoney.minorUnits("0", czk))
        assertEquals(500, PosMoney.minorUnits(" 500 ", jpy))
    }

    @Test
    fun `sub-minor values and non-numbers fail instead of rounding`() {
        assertFailsWith<IllegalArgumentException> { PosMoney.minorUnits("10.005", czk) }
        assertFailsWith<IllegalArgumentException> { PosMoney.minorUnits("500.5", jpy) }
        assertFailsWith<IllegalArgumentException> { PosMoney.minorUnits("ten", czk) }
        assertFailsWith<IllegalArgumentException> { PosMoney.minorUnits("", czk) }
    }

    @Test
    fun `minor units format back to the POS decimal string`() {
        assertEquals("129.00", PosMoney.decimal(12900, czk))
        assertEquals("0.05", PosMoney.decimal(5, czk))
        assertEquals("500", PosMoney.decimal(500, jpy))
        assertEquals(12345, PosMoney.minorUnits(PosMoney.decimal(12345, czk), czk))
    }
}
