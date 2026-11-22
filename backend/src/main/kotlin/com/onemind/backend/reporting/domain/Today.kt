package com.onemind.backend.reporting.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** One stored hour of a venue's day (`reporting.hourly_sales`), in minor units of the venue currency. */
data class HourTotals(
    val day: LocalDate,
    val hour: Int,
    val revenueMinor: Long,
    val refundedMinor: Long,
    val tipsMinor: Long,
    val guests: Int,
    val discountMinor: Long,
    val deletedMinor: Long,
    val paymentMismatchBills: Int,
    /** Bills that count in [paidMinor] (V013; 0 on a day stored before RollupVersion 2). */
    val paidBills: Int,
    /** Refunded bills (V014; 0 on a day stored before RollupVersion 3). */
    val refundedBills: Int,
    /** Whole deleted bills. */
    val deletedBills: Int,
    /** Card / cash / other of [paidMinor]: refunded bills already taken off (0 before RollupVersion 2). */
    val paidCardMinor: Long,
    val paidCashMinor: Long,
    val paidOtherMinor: Long,
) {
    /** `paidRevenue` of the hour: revenue without refunded bills (M13). */
    val paidMinor: Long get() = revenueMinor - refundedMinor
}

/** One venue's input to Today: its current business day's hours and the last same weekdays that have data. */
data class VenueToday(
    val today: List<HourTotals>,
    /** Hours of the last 4 same weekdays, by day; a day missing here has no rollups. */
    val sameWeekdays: Map<LocalDate, List<HourTotals>>,
)

enum class BarKind { PAST, CURRENT, FORECAST }

data class Bar(
    val hour: Int,
    val kind: BarKind,
    val amountMinor: Long?,
)

data class LossTotals(
    val discountsMinor: Long,
    val refundsMinor: Long,
    val deletedBillsMinor: Long,
) {
    val totalMinor: Long get() = discountsMinor + refundsMinor + deletedBillsMinor
}

data class TodayFigures(
    val paidMinor: Long,
    val tipsMinor: Long,
    val guests: Int,
    val perGuestMinor: Long?,
    val loss: LossTotals,
    val bars: List<Bar>,
    val paymentMismatchBills: Int,
)

/**
 * Screen 01 from the rollups of the venues that have data (`docs/domain/METRICS.md`). Pure. Hours are
 * local clock hours; several venues add up hour by hour.
 *
 * - Paid so far = revenue − refunded of the current business day; per guest = paid / guests (null
 *   without guests), rounded half-up to the minor unit.
 * - Possible loss = discounts + refunds + whole deleted bills (first release components).
 * - Hourly chart (M12 closed): the hours that have sales, i.e. the hours with paid revenue today or on
 *   any of the last 4 same weekdays, from the first to the last in business-day order (the day's own
 *   local hours: none for the hour skipped on the spring DST day), plus the current hour. Hours before the current one are `PAST`, the current one `CURRENT`
 *   (paid so far), later ones `FORECAST`: the average of that hour over those of the last 4 same
 *   weekdays that have data (a day with data but no sale in the hour counts 0), rounded half-up; null
 *   when there is no such day. For several venues the forecast is the sum of each venue's average.
 */
object TodayCalculator {
    /** [dayHours]: the local hours of the business day in order (`BusinessDayCalendar.localHours`). */
    fun compute(
        venues: List<VenueToday>,
        currentHour: Int,
        dayHours: List<Int>,
    ): TodayFigures {
        val todayHours = venues.flatMap { it.today }
        val paid = todayHours.sumOf { it.paidMinor }
        val guests = todayHours.sumOf { it.guests }
        return TodayFigures(
            paidMinor = paid,
            tipsMinor = todayHours.sumOf { it.tipsMinor },
            guests = guests,
            perGuestMinor = if (guests > 0) divideHalfUp(paid, guests.toLong()) else null,
            loss =
                LossTotals(
                    discountsMinor = todayHours.sumOf { it.discountMinor },
                    refundsMinor = todayHours.sumOf { it.refundedMinor },
                    deletedBillsMinor = todayHours.sumOf { it.deletedMinor },
                ),
            bars = bars(venues, currentHour, dayHours),
            paymentMismatchBills = todayHours.sumOf { it.paymentMismatchBills },
        )
    }

    /** `(current − previous) / previous × 100`, one decimal, half-up; null when previous is 0 or missing. */
    fun deltaPercent(
        currentMinor: Long,
        previousMinor: Long?,
    ): BigDecimal? {
        if (previousMinor == null || previousMinor == 0L) return null
        return BigDecimal
            .valueOf(currentMinor - previousMinor)
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(previousMinor), 1, RoundingMode.HALF_UP)
    }

    private fun bars(
        venues: List<VenueToday>,
        currentHour: Int,
        dayHours: List<Int>,
    ): List<Bar> {
        // Business-day order; an hour the day doesn't have (spring DST) never gets a bar.
        val position = { hour: Int -> dayHours.indexOf(hour) }
        val today = venues.flatMap { it.today }.groupBy { it.hour }.mapValues { (_, hours) -> hours.sumOf { it.paidMinor } }
        val forecasts = venues.map { forecast(it) }
        val withSales =
            today.filterValues { it > 0 }.keys +
                venues.flatMap { venue ->
                    venue.sameWeekdays.values
                        .flatten()
                        .filter { it.paidMinor > 0 }
                        .map { it.hour }
                }
        if (withSales.isEmpty()) return emptyList()
        val positions = (withSales + currentHour).map(position).filter { it >= 0 }
        if (positions.isEmpty()) return emptyList()
        return (positions.min()..positions.max()).map { at ->
            val hour = dayHours[at]
            when {
                at < position(currentHour) -> {
                    Bar(hour, BarKind.PAST, today[hour] ?: 0)
                }

                at == position(currentHour) -> {
                    Bar(hour, BarKind.CURRENT, today[hour] ?: 0)
                }

                else -> {
                    // Venues without any earlier same weekday add nothing; none at all: no forecast.
                    val known = forecasts.filterNotNull()
                    Bar(hour, BarKind.FORECAST, if (known.isEmpty()) null else known.sumOf { it[hour] ?: 0 })
                }
            }
        }
    }

    /** The venue's average paid revenue per hour over its same weekdays with data; null without any. */
    private fun forecast(venue: VenueToday): Map<Int, Long>? {
        val days = venue.sameWeekdays
        if (days.isEmpty()) return null
        val perHour =
            days.values
                .flatten()
                .groupBy { it.hour }
                .mapValues { (_, hours) -> hours.sumOf { it.paidMinor } }
        return perHour.mapValues { (_, sum) -> divideHalfUp(sum, days.size.toLong()) }
    }

    private fun divideHalfUp(
        amount: Long,
        divisor: Long,
    ): Long = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValueExact()
}
