package com.onemind.core.domain.quantity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuantityTest {
    private fun d(text: String) = DecimalValue.parse(text)

    private val bottle = QuantityUnit("BOTTLE")

    @Test
    fun parsesAndPrintsTheApiForm() {
        listOf("1.25", "0.6000", "-3.5", "12", "0.05", "-0.05", "0").forEach { assertEquals(it, d(it).toString()) }
    }

    @Test
    fun arithmeticIsExact() {
        // 0.1 + 0.2 is the classic floating-point trap.
        assertEquals(d("0.3"), d("0.1") + d("0.2"))
        assertEquals("0.30", (d("0.10") + d("0.2")).toString())
        assertEquals(d("-1.75"), d("1.25") - d("3"))
        assertTrue(d("-0.01").isNegative)
    }

    @Test
    fun equalityAndOrderIgnoreTrailingZeros() {
        assertEquals(d("1.50"), d("1.5"))
        assertEquals(d("1.50").hashCode(), d("1.5").hashCode())
        assertEquals(listOf(d("-2"), d("0.05"), d("0.5"), d("1.25")), listOf(d("1.25"), d("0.05"), d("-2"), d("0.5")).sorted())
    }

    @Test
    fun rejectsAnythingButPlainDecimals() {
        listOf("", "1,5", "1.", ".5", "1e3", " 1", "+1", "1.0000000001", "99999999999999999999").forEach {
            assertFailsWith<IllegalArgumentException>(it) { d(it) }
        }
    }

    @Test
    fun overflowFails() {
        assertFailsWith<IllegalStateException> { d("9223372036854775807") + d("1") }
        assertFailsWith<IllegalStateException> { d("922337203685477581") + d("0.1") }
    }

    @Test
    fun quantitiesOfOneUnitAdd() {
        assertEquals(Quantity(d("4.25"), bottle), Quantity(d("3"), bottle) + Quantity(d("1.25"), bottle))
        assertFailsWith<IllegalArgumentException> { Quantity(d("1"), bottle) + Quantity(d("1"), QuantityUnit("ML")) }
    }
}
