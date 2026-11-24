package com.onemind.sales.presentation.sales

import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.presentation.format.VenueLocale
import com.onemind.core.presentation.format.formatClockTime
import com.onemind.core.presentation.format.formatCount
import com.onemind.core.presentation.format.formatDeltaPercent
import com.onemind.core.presentation.format.formatMoney
import com.onemind.sales.domain.SalesDataStatus
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRange
import com.onemind.sales.domain.SalesSummary
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/** Screen 02 as formatted text; a `null` figure is drawn as the empty value, never as 0 (rule 2). */
data class SalesUi(
    val range: SalesRange,
    val waitingForFirstSync: Boolean,
    val pendingVenueCount: Int,
    val revenue: String?,
    val comparison: SalesComparisonUi?,
    val guests: String?,
    val averageBill: String?,
    val perGuest: String?,
    val tips: String?,
    val payment: PaymentSplitUi?,
    /** The period's possible loss as a negative amount (`−22 410 Kč`), for the 02c link. */
    val possibleLoss: String?,
    val dataGaps: List<SalesDataGapLine>,
    val updatedAt: String?,
)

/** *+8.1% vs last week*; [previousWeekday] names the compared day (Hour, Day), [previousMonth] the month (Month). */
data class SalesComparisonUi(
    val delta: String,
    val tone: BadgeTone,
    val period: SalesPeriod,
    val previousWeekday: DayOfWeek,
    val previousMonth: Month,
)

/** Card and cash shares of card + cash (other methods are left out of the split, M4). */
data class PaymentSplitUi(
    val cardPercent: Int,
    val card: String,
    val cash: String,
) {
    val cashPercent: Int get() = PERCENT - cardPercent
}

data class SalesDataGapLine(
    val kind: SalesDataGapKind,
    val count: Int,
)

enum class SalesDataGapKind { UNREADABLE, WITHOUT_ITEMS, OUTDATED, PAYMENT_MISMATCH }

private const val PERCENT = 100

fun SalesSummary.toUi(): SalesUi {
    val locale = VenueLocale.forCurrency(currency)
    val ready = status == SalesDataStatus.READY
    return SalesUi(
        range = range,
        waitingForFirstSync = !ready,
        pendingVenueCount = if (ready) pendingVenueIds.size else 0,
        revenue = revenue?.let { formatMoney(it, locale) },
        comparison =
            comparison?.let { c ->
                c.deltaPercent?.let { delta ->
                    SalesComparisonUi(
                        delta = formatDeltaPercent(delta),
                        tone =
                            when {
                                delta.isZero -> BadgeTone.Neutral
                                delta.isNegative -> BadgeTone.Negative
                                else -> BadgeTone.Positive
                            },
                        period = range.period,
                        previousWeekday = c.previousFrom.dayOfWeek,
                        previousMonth = c.previousFrom.month,
                    )
                }
            },
        guests = guests?.let { formatCount(it.toLong(), locale) },
        averageBill = averageBill?.let { formatMoney(it, locale) },
        perGuest = perGuest?.let { formatMoney(it, locale) },
        tips = tips?.let { formatMoney(it, locale) },
        payment =
            byPayment?.let { split ->
                split.cardPercent?.let { percent ->
                    PaymentSplitUi(
                        cardPercent = percent.coerceIn(0, PERCENT),
                        card = formatMoney(split.card, locale),
                        cash = formatMoney(split.cash, locale),
                    )
                }
            },
        possibleLoss = possibleLoss?.let { formatMoney(if (it.isZero) it else -it, locale) },
        dataGaps =
            listOf(
                SalesDataGapLine(SalesDataGapKind.UNREADABLE, dataGaps.quarantinedBillCount),
                SalesDataGapLine(SalesDataGapKind.WITHOUT_ITEMS, dataGaps.linelessBillCount),
                SalesDataGapLine(SalesDataGapKind.OUTDATED, dataGaps.outdatedBillCount),
                SalesDataGapLine(SalesDataGapKind.PAYMENT_MISMATCH, dataGaps.paymentMismatchBillCount),
            ).filter { it.count > 0 },
        // The oldest sync of the venues with data: every figure is at least this fresh.
        updatedAt =
            venues
                .filter { it.status == SalesDataStatus.READY }
                .mapNotNull { v -> v.dataAsOf?.let { it to v.timeZone } }
                .minByOrNull { it.first }
                ?.let { (instant, zone) -> formatClockTime(instant, zone) },
    )
}
