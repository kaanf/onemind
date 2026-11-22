package com.onemind.core.presentation.format

import com.onemind.core.domain.quantity.DecimalValue
import kotlin.test.Test
import kotlin.test.assertEquals

class DeltaFormatTest {
    @Test
    fun positiveDeltasShowAPlus() = assertEquals("+12.4%", formatDeltaPercent(DecimalValue.parse("12.4")))

    @Test
    fun negativeDeltasUseTheMinusSign() = assertEquals("−3.0%", formatDeltaPercent(DecimalValue.parse("-3.0")))

    @Test
    fun zeroHasNoSign() {
        assertEquals("0.0%", formatDeltaPercent(DecimalValue.parse("0.0")))
        assertEquals("0.0%", formatDeltaPercent(DecimalValue.parse("-0.0")))
    }

    @Test
    fun theDigitsStayAsSent() = assertEquals("+150.0%", formatDeltaPercent(DecimalValue.parse("150.0")))
}
