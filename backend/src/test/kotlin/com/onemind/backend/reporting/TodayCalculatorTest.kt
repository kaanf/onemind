package com.onemind.backend.reporting

import com.onemind.backend.reporting.domain.BarKind
import com.onemind.backend.reporting.domain.HourTotals
import com.onemind.backend.reporting.domain.TodayCalculator
import com.onemind.backend.reporting.domain.VenueToday
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Screen 01 definitions (`docs/domain/METRICS.md`), on the 05:00 cut-off. */
class TodayCalculatorTest {
    private val today = LocalDate.parse("2026-11-20")

    private fun hour(
        hour: Int,
        paid: Long,
        day: LocalDate = today,
        refunded: Long = 0,
        guests: Int = 0,
        tips: Long = 0,
    ) = HourTotals(day, hour, paid + refunded, refunded, tips, guests, 0, 0, 0)

    private fun weeksAgo(weeks: Long) = today.minusWeeks(weeks)

    @Test
    fun `the axis runs over the hours with sales in business-day order, across midnight`() {
        val venue = VenueToday(today = listOf(hour(18, 1_000), hour(19, 2_000), hour(0, 500)), sameWeekdays = emptyMap())

        val bars = TodayCalculator.compute(listOf(venue), currentHour = 20, cutOffHour = 5).bars

        assertEquals(listOf(18, 19, 20, 21, 22, 23, 0), bars.map { it.hour })
        assertEquals(listOf(BarKind.PAST, BarKind.PAST, BarKind.CURRENT), bars.take(3).map { it.kind })
        assertEquals(listOf(1_000L, 2_000L, 0L), bars.take(3).map { it.amountMinor })
        assertEquals(BarKind.FORECAST, bars.last().kind)
        assertNull(bars.last().amountMinor, "no earlier same weekday: no forecast")
    }

    @Test
    fun `future hours are the average of the same weekdays that have data, a missing hour counting 0`() {
        val venue =
            VenueToday(
                today = listOf(hour(18, 1_000)),
                sameWeekdays =
                    mapOf(
                        weeksAgo(1) to listOf(hour(18, 900, weeksAgo(1)), hour(22, 3_001, weeksAgo(1))),
                        weeksAgo(2) to listOf(hour(18, 800, weeksAgo(2))),
                    ),
            )

        val bars = TodayCalculator.compute(listOf(venue), currentHour = 19, cutOffHour = 5).bars.associateBy { it.hour }

        // (3 001 + 0) / 2 = 1 500.5 → 1 501 (half-up).
        assertEquals(1_501L, bars.getValue(22).amountMinor)
        assertEquals(0L, bars.getValue(20).amountMinor)
        assertEquals(BarKind.FORECAST, bars.getValue(22).kind)
        assertEquals(listOf(18, 19, 20, 21, 22), bars.keys.toList())
    }

    @Test
    fun `several venues add up hour by hour, forecasts too`() {
        val a = VenueToday(listOf(hour(20, 1_000)), mapOf(weeksAgo(1) to listOf(hour(21, 400, weeksAgo(1)))))
        val b = VenueToday(listOf(hour(20, 500)), emptyMap())

        val figures = TodayCalculator.compute(listOf(a, b), currentHour = 20, cutOffHour = 5)

        assertEquals(1_500, figures.paidMinor)
        assertEquals(1_500L, figures.bars.first { it.hour == 20 }.amountMinor)
        assertEquals(400L, figures.bars.first { it.hour == 21 }.amountMinor, "b has no earlier weekday and adds nothing")
    }

    @Test
    fun `paid so far leaves out refunds, per guest is rounded half-up and absent without guests`() {
        val venue = VenueToday(listOf(hour(20, 1_000, refunded = 300, guests = 3, tips = 50)), emptyMap())

        val figures = TodayCalculator.compute(listOf(venue), currentHour = 21, cutOffHour = 5)

        assertEquals(1_000, figures.paidMinor)
        assertEquals(333, figures.perGuestMinor)
        assertEquals(300, figures.loss.refundsMinor)
        assertEquals(50, figures.tipsMinor)
        assertNull(TodayCalculator.compute(listOf(VenueToday(listOf(hour(20, 1_000)), emptyMap())), 21, 5).perGuestMinor)
    }

    @Test
    fun `the delta is one decimal half-up and absent without a previous figure`() {
        assertEquals(BigDecimal("12.4"), TodayCalculator.deltaPercent(4_832_000, 4_299_000))
        assertEquals(BigDecimal("66.7"), TodayCalculator.deltaPercent(5, 3))
        assertEquals(BigDecimal("-3.0"), TodayCalculator.deltaPercent(970, 1_000))
        assertNull(TodayCalculator.deltaPercent(1_000, 0))
        assertNull(TodayCalculator.deltaPercent(1_000, null))
    }

    @Test
    fun `no sales anywhere means no axis`() {
        assertEquals(emptyList(), TodayCalculator.compute(listOf(VenueToday(emptyList(), emptyMap())), 20, 5).bars)
    }
}
