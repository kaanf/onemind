package com.onemind.backend.reporting

import com.onemind.backend.reporting.domain.HourTotals
import com.onemind.backend.reporting.domain.SalesCalculator
import com.onemind.backend.reporting.domain.SalesPeriod
import com.onemind.backend.reporting.domain.SalesRanges
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SalesCalculatorTest {
    private fun d(date: String) = LocalDate.parse(date)

    private fun days(
        from: String,
        to: String,
    ) = generateSequence(d(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(d(to)) }.toList()

    private fun hour(
        paid: Long,
        refunded: Long = 0,
        paidBills: Int = 1,
        guests: Int = 2,
        card: Long = paid,
        cash: Long = 0,
        other: Long = 0,
    ) = HourTotals(
        day = d("2026-11-20"),
        hour = 20,
        revenueMinor = paid + refunded,
        refundedMinor = refunded,
        tipsMinor = 500,
        guests = guests,
        discountMinor = 300,
        deletedMinor = 200,
        paymentMismatchBills = 0,
        paidBills = paidBills,
        refundedBills = 0,
        deletedBills = 0,
        paidCardMinor = card,
        paidCashMinor = cash,
        paidOtherMinor = other,
    )

    @Test
    fun `an unfinished week is compared with the same elapsed span, the last day up to the same time (M3)`() {
        // Wednesday 18 November 2026.
        val ranges = SalesRanges.of(SalesPeriod.WEEK, d("2026-11-18"), today = d("2026-11-18"))

        assertEquals(d("2026-11-16"), ranges.current.from)
        assertEquals(d("2026-11-22"), ranges.current.to)
        assertFalse(ranges.finished)
        assertEquals(d("2026-11-09"), ranges.previous.from)
        assertEquals(d("2026-11-15"), ranges.previous.to)
        assertEquals(days("2026-11-09", "2026-11-10"), ranges.comparedWholeDays)
        assertEquals(d("2026-11-11"), ranges.comparedCutDay)
        assertEquals(days("2026-11-16", "2026-11-18"), ranges.elapsedDays(d("2026-11-18")))
    }

    @Test
    fun `an unfinished week anchored on an earlier day of it is the same as today's week`() {
        assertEquals(
            SalesRanges.of(SalesPeriod.WEEK, d("2026-11-18"), today = d("2026-11-18")),
            SalesRanges.of(SalesPeriod.WEEK, d("2026-11-16"), today = d("2026-11-18")),
        )
    }

    @Test
    fun `on Monday the week is compared with last Monday up to the same time`() {
        val ranges = SalesRanges.of(SalesPeriod.WEEK, d("2026-11-16"), today = d("2026-11-16"))

        assertEquals(emptyList(), ranges.comparedWholeDays)
        assertEquals(d("2026-11-09"), ranges.comparedCutDay)
    }

    @Test
    fun `a finished week is compared with the whole previous week`() {
        val ranges = SalesRanges.of(SalesPeriod.WEEK, d("2026-11-04"), today = d("2026-11-18"))

        assertTrue(ranges.finished)
        assertEquals(days("2026-10-26", "2026-11-01"), ranges.comparedWholeDays)
        assertNull(ranges.comparedCutDay)
        assertEquals(days("2026-11-02", "2026-11-08"), ranges.elapsedDays(d("2026-11-18")))
    }

    @Test
    fun `a month is compared up to day N of the previous month`() {
        val ranges = SalesRanges.of(SalesPeriod.MONTH, d("2027-03-28"), today = d("2027-03-28"))

        assertEquals(d("2027-03-01"), ranges.current.from)
        assertEquals(d("2027-03-31"), ranges.current.to)
        assertEquals(days("2027-02-01", "2027-02-27"), ranges.comparedWholeDays)
        assertEquals(d("2027-02-28"), ranges.comparedCutDay)
    }

    @Test
    fun `a month past the previous month's last day is compared with the whole previous month`() {
        // 31 March → the whole of February (no 31 February to cut at).
        val ranges = SalesRanges.of(SalesPeriod.MONTH, d("2027-03-31"), today = d("2027-03-31"))

        assertFalse(ranges.finished)
        assertEquals(days("2027-02-01", "2027-02-28"), ranges.comparedWholeDays)
        assertNull(ranges.comparedCutDay)
        assertEquals(d("2027-02-28"), ranges.comparedTo)
    }

    @Test
    fun `a day and an hour are compared with the same weekday last week`() {
        val day = SalesRanges.of(SalesPeriod.DAY, d("2026-11-20"), today = d("2026-11-20"))
        assertEquals(d("2026-11-13"), day.comparedCutDay)
        assertEquals(emptyList(), day.comparedWholeDays)

        val pastDay = SalesRanges.of(SalesPeriod.DAY, d("2026-11-19"), today = d("2026-11-20"))
        assertEquals(listOf(d("2026-11-12")), pastDay.comparedWholeDays)
        assertNull(pastDay.comparedCutDay)

        val hour = SalesRanges.of(SalesPeriod.HOUR, d("2026-11-20"), today = d("2026-11-20"))
        assertEquals(d("2026-11-13"), hour.comparedCutDay)
    }

    @Test
    fun `the week of the spring DST change is a normal week of business days`() {
        // Sunday 29 March 2026 is the 23-hour day in Europe/Prague; ranges are business days, so nothing shifts.
        val ranges = SalesRanges.of(SalesPeriod.WEEK, d("2026-03-29"), today = d("2026-03-29"))

        assertEquals(days("2026-03-16", "2026-03-21"), ranges.comparedWholeDays)
        assertEquals(d("2026-03-22"), ranges.comparedCutDay)
    }

    @Test
    fun `a future anchor and an hour of another day are refused`() {
        assertThrows<IllegalArgumentException> { SalesRanges.of(SalesPeriod.DAY, d("2026-11-21"), today = d("2026-11-20")) }
        assertThrows<IllegalArgumentException> { SalesRanges.of(SalesPeriod.HOUR, d("2026-11-19"), today = d("2026-11-20")) }
    }

    @Test
    fun `average bill divides revenue by paid bills, refunded revenue already off`() {
        val figures = SalesCalculator.compute(listOf(hour(paid = 10_000, refunded = 3_000, paidBills = 3)), upToDate = true)

        assertEquals(10_000, figures.revenueMinor)
        assertEquals(3_333, figures.averageBillMinor)
        assertEquals(5_000, figures.perGuestMinor)
        assertEquals(500, figures.tipsMinor)
        assertEquals(3_500, figures.loss.totalMinor)
    }

    @Test
    fun `card percent is card over card and cash, half-up, other left out (M4)`() {
        val figures = SalesCalculator.compute(listOf(hour(paid = 10_000, card = 6_850, cash = 3_150 - 1_000, other = 1_000)), true)
        val payments = requireNotNull(figures.payments)

        assertEquals(6_850 + 2_150 + 1_000, payments.cardMinor + payments.cashMinor + payments.otherMinor)
        // 6850 / 9000 = 76.11 %.
        assertEquals(76, payments.cardPercent)
        assertNull(SalesCalculator.compute(listOf(hour(paid = 1_000, card = 0, other = 1_000)), true).payments?.cardPercent)
    }

    @Test
    fun `days stored by an older calculation give no average bill and no split, never a guess (rule 2)`() {
        val figures = SalesCalculator.compute(listOf(hour(paid = 10_000)), upToDate = false)

        assertEquals(10_000, figures.revenueMinor)
        assertNull(figures.averageBillMinor)
        assertNull(figures.payments)
    }

    @Test
    fun `no paid bills and no guests give no average and no per guest`() {
        val figures = SalesCalculator.compute(listOf(hour(paid = 0, paidBills = 0, guests = 0)), true)

        assertNull(figures.averageBillMinor)
        assertNull(figures.perGuestMinor)
        assertEquals(0, figures.revenueMinor)
    }
}
