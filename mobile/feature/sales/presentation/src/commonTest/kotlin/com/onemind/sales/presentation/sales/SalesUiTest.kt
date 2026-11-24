package com.onemind.sales.presentation.sales

import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.sales.domain.PaymentSplit
import com.onemind.sales.domain.SalesDataGaps
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.presentation.sales.SalesFixtures.czk
import com.onemind.sales.presentation.sales.SalesFixtures.shown
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SalesUiTest {
    @Test
    fun formatsEveryFigureOfAReadyWeek() {
        val ui = SalesFixtures.week.toUi()

        assertEquals(shown("312·480_Kč"), ui.revenue)
        assertEquals(SalesComparisonUi("+8.1%", BadgeTone.Positive, SalesPeriod.WEEK, DayOfWeek.MONDAY), ui.comparison)
        assertEquals(shown("1·846"), ui.guests)
        assertEquals(shown("412_Kč"), ui.averageBill)
        assertEquals(shown("169_Kč"), ui.perGuest)
        assertEquals(shown("15·200_Kč"), ui.tips)
        assertEquals(PaymentSplitUi(68, shown("212·490_Kč"), shown("99·990_Kč")), ui.payment)
        assertEquals(32, ui.payment?.cashPercent)
        assertEquals("21:38", ui.updatedAt)
        assertTrue(ui.dataGaps.isEmpty())
        assertEquals(false, ui.waitingForFirstSync)
    }

    @Test
    fun possibleLossIsShownAsANegativeAmount() {
        assertEquals(shown("−22·410_Kč"), SalesFixtures.week.toUi().possibleLoss)
        assertEquals(
            shown("0_Kč"),
            SalesFixtures.week
                .copy(possibleLoss = czk(0))
                .toUi()
                .possibleLoss,
        )
    }

    @Test
    fun noDataYetKeepsEveryFigureEmptyNotZero() {
        val ui = SalesFixtures.noDataYet.toUi()

        assertTrue(ui.waitingForFirstSync)
        assertEquals(0, ui.pendingVenueCount)
        assertNull(ui.revenue)
        assertNull(ui.comparison)
        assertNull(ui.guests)
        assertNull(ui.averageBill)
        assertNull(ui.perGuest)
        assertNull(ui.tips)
        assertNull(ui.payment)
        assertNull(ui.possibleLoss)
        assertNull(ui.updatedAt)
    }

    @Test
    fun aSplitWithoutCardPercentDrawsNoBar() {
        val ui = SalesFixtures.week.copy(byPayment = PaymentSplit(czk(0), czk(0), czk(500), cardPercent = null)).toUi()

        assertNull(ui.payment)
    }

    @Test
    fun negativeAndZeroDeltasGetTheirTone() {
        val week = SalesFixtures.week
        val down = week.copy(comparison = week.comparison!!.copy(deltaPercent = DecimalValue.parse("-3.0"))).toUi()
        val flat = week.copy(comparison = week.comparison!!.copy(deltaPercent = DecimalValue.parse("0.0"))).toUi()
        val none = week.copy(comparison = week.comparison!!.copy(deltaPercent = null)).toUi()

        assertEquals(BadgeTone.Negative, down.comparison?.tone)
        assertEquals("−3.0%", down.comparison?.delta)
        assertEquals(BadgeTone.Neutral, flat.comparison?.tone)
        assertNull(none.comparison)
    }

    @Test
    fun onlyNonZeroDataGapsAreListed() {
        val ui = SalesFixtures.week.copy(dataGaps = SalesDataGaps(2, 0, 0, 1)).toUi()

        assertEquals(
            listOf(SalesDataGapLine(SalesDataGapKind.UNREADABLE, 2), SalesDataGapLine(SalesDataGapKind.PAYMENT_MISMATCH, 1)),
            ui.dataGaps,
        )
    }

    @Test
    fun pendingVenuesAreCountedOnlyWhenSomeHaveData() {
        val ui = SalesFixtures.week.copy(pendingVenueIds = listOf(SalesFixtures.VENUE_B)).toUi()

        assertEquals(1, ui.pendingVenueCount)
    }
}
