package com.onemind.backend.reporting.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class SalesPeriod { HOUR, DAY, WEEK, MONTH }

/** Business days `from..to`, both inclusive. */
data class DaySpan(
    val from: LocalDate,
    val to: LocalDate,
) {
    val days: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}

/**
 * The days a Sales period covers and what it is compared with (`docs/domain/METRICS.md`, periods).
 *
 * - [current]: the whole period (the screen's range label); figures cover it up to today.
 * - [previous]: the whole previous period (label).
 * - [comparedWholeDays] + [comparedCutDay]: the part of [previous] the figures are compared with. A
 *   finished period is compared with the whole previous one ([comparedCutDay] null). An unfinished one
 *   with the same elapsed span (M3 default): as many whole business days from the start, then
 *   [comparedCutDay] up to the same local time as now. A month whose elapsed day N doesn't exist in the
 *   previous month (31 March → February) is compared with the whole previous month.
 */
data class SalesRanges(
    val period: SalesPeriod,
    val current: DaySpan,
    val finished: Boolean,
    val previous: DaySpan,
    val comparedWholeDays: List<LocalDate>,
    val comparedCutDay: LocalDate?,
) {
    /** The first day of the compared span: a venue counts in the comparison only if its data starts by then. */
    val comparedFrom: LocalDate get() = (comparedWholeDays + listOfNotNull(comparedCutDay)).min()

    /** The last day of the compared span. */
    val comparedTo: LocalDate get() = (comparedWholeDays + listOfNotNull(comparedCutDay)).max()

    /** The current days that have figures: the period up to [today]. */
    fun elapsedDays(today: LocalDate): List<LocalDate> = current.days.filter { !it.isAfter(today) }

    companion object {
        /**
         * The ranges of [period] containing [anchor] (a business day, not after [today]). `HOUR` is the
         * current clock hour, so only today.
         */
        fun of(
            period: SalesPeriod,
            anchor: LocalDate,
            today: LocalDate,
        ): SalesRanges {
            require(!anchor.isAfter(today)) { "the anchor is not in the future (the API validates it)" }
            return when (period) {
                SalesPeriod.HOUR -> {
                    require(anchor == today) { "HOUR is today's current hour (the API validates it)" }
                    val lastWeek = today.minusWeeks(1)
                    SalesRanges(period, DaySpan(today, today), false, DaySpan(lastWeek, lastWeek), emptyList(), lastWeek)
                }

                SalesPeriod.DAY -> {
                    span(period, DaySpan(anchor, anchor), DaySpan(anchor.minusWeeks(1), anchor.minusWeeks(1)), today)
                }

                SalesPeriod.WEEK -> {
                    val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    span(period, DaySpan(monday, monday.plusDays(6)), DaySpan(monday.minusWeeks(1), monday.minusDays(1)), today)
                }

                SalesPeriod.MONTH -> {
                    val first = anchor.withDayOfMonth(1)
                    val previousFirst = first.minusMonths(1)
                    span(
                        period,
                        DaySpan(first, first.with(TemporalAdjusters.lastDayOfMonth())),
                        DaySpan(previousFirst, first.minusDays(1)),
                        today,
                    )
                }
            }
        }

        private fun span(
            period: SalesPeriod,
            current: DaySpan,
            previous: DaySpan,
            today: LocalDate,
        ): SalesRanges {
            if (current.to.isBefore(today)) {
                return SalesRanges(period, current, true, previous, previous.days, null)
            }
            // Day N of the period (0-based) is today; the previous period's day N is cut at the same time.
            val elapsed = ChronoUnit.DAYS.between(current.from, today)
            val cutDay = previous.from.plusDays(elapsed)
            if (cutDay.isAfter(previous.to)) {
                return SalesRanges(period, current, false, previous, previous.days, null)
            }
            return SalesRanges(period, current, false, previous, DaySpan(previous.from, cutDay).days.dropLast(1), cutDay)
        }
    }
}

data class PaymentSplit(
    val cardMinor: Long,
    val cashMinor: Long,
    val otherMinor: Long,
    /** `card / (card + cash)` as a whole percent, half-up; other payments left out (M4). Null without card or cash. */
    val cardPercent: Int?,
)

data class SalesFigures(
    val revenueMinor: Long,
    val guests: Int,
    val tipsMinor: Long,
    /** Null without paid bills, or while a day of the span is still stored by an older calculation (rule 2). */
    val averageBillMinor: Long?,
    val perGuestMinor: Long?,
    /** Null while a day of the span is still stored by an older calculation (rule 2). */
    val payments: PaymentSplit?,
    val loss: LossTotals,
    val paymentMismatchBills: Int,
)

/**
 * Screen 02 from the stored hours of the venues that have data (`docs/domain/METRICS.md`). Pure.
 *
 * - Revenue = `paidRevenue` (revenue − refunded); guests, tips and possible loss as on Today.
 * - Average bill = revenue / paid bills (not deleted, not refunded, payments add up), half-up.
 * - Per guest = revenue / guests, half-up; null without guests.
 * - By payment: card / cash / other of revenue (refunded bills taken off); `cardPercent` = card / (card + cash).
 */
object SalesCalculator {
    /** [hours]: the stored hours of the span; [upToDate]: false while one of its days predates RollupVersion 2. */
    fun compute(
        hours: List<HourTotals>,
        upToDate: Boolean,
    ): SalesFigures {
        val revenue = hours.sumOf { it.paidMinor }
        val paidBills = hours.sumOf { it.paidBills }
        val guests = hours.sumOf { it.guests }
        return SalesFigures(
            revenueMinor = revenue,
            guests = guests,
            tipsMinor = hours.sumOf { it.tipsMinor },
            averageBillMinor = if (upToDate && paidBills > 0) divideHalfUp(revenue, paidBills.toLong()) else null,
            perGuestMinor = if (guests > 0) divideHalfUp(revenue, guests.toLong()) else null,
            payments = if (upToDate) split(hours) else null,
            loss =
                LossTotals(
                    discountsMinor = hours.sumOf { it.discountMinor },
                    refundsMinor = hours.sumOf { it.refundedMinor },
                    deletedBillsMinor = hours.sumOf { it.deletedMinor },
                ),
            paymentMismatchBills = hours.sumOf { it.paymentMismatchBills },
        )
    }

    private fun split(hours: List<HourTotals>): PaymentSplit {
        val card = hours.sumOf { it.paidCardMinor }
        val cash = hours.sumOf { it.paidCashMinor }
        return PaymentSplit(
            cardMinor = card,
            cashMinor = cash,
            otherMinor = hours.sumOf { it.paidOtherMinor },
            cardPercent = if (card + cash > 0) divideHalfUp(card * 100, card + cash).toInt() else null,
        )
    }

    private fun divideHalfUp(
        amount: Long,
        divisor: Long,
    ): Long = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValueExact()
}
