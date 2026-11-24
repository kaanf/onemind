package com.onemind.sales.domain

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Screen 02 (`GET /dashboard/sales`, `docs/domain/METRICS.md`). Figures are `null` while no requested
 * venue has data ([status] `NO_DATA_YET`), never 0; a venue with data and no sales has real zeros.
 */
data class SalesSummary(
    val status: SalesDataStatus,
    val currency: Currency,
    val range: SalesRange,
    val venues: List<SalesVenue>,
    val pendingVenueIds: List<String>,
    val revenue: Money?,
    val comparison: SalesComparison?,
    val guests: Int?,
    val averageBill: Money?,
    val perGuest: Money?,
    val tips: Money?,
    val byPayment: PaymentSplit?,
    /** The period's possible loss total, shown on the Discounts & losses link. */
    val possibleLoss: Money?,
    val dataGaps: SalesDataGaps,
)

/** The periods of screen 02 (Hour | Day | Week | Month); the server's default is [WEEK]. */
enum class SalesPeriod { HOUR, DAY, WEEK, MONTH }

enum class SalesDataStatus { READY, NO_DATA_YET }

/** The whole period ("2 to 8 Nov"); [hour] only for [SalesPeriod.HOUR]. */
data class SalesRange(
    val period: SalesPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val hour: Int?,
    val finished: Boolean,
)

data class SalesVenue(
    val venueId: String,
    val businessDay: LocalDate,
    val timeZone: TimeZone,
    val status: SalesDataStatus,
    val dataAsOf: Instant?,
)

/** Revenue against the previous period ([previousFrom]..[previousTo]); [deltaPercent] as sent. */
data class SalesComparison(
    val previousFrom: LocalDate,
    val previousTo: LocalDate,
    val upToSameTime: Boolean,
    val current: Money,
    val previousRevenue: Money,
    val deltaPercent: DecimalValue?,
)

/** Revenue by payment method; [cardPercent] is card / (card + cash), `null` without card or cash. */
data class PaymentSplit(
    val card: Money,
    val cash: Money,
    val other: Money,
    val cardPercent: Int?,
)

/** What the figures are missing (rule 2); the screen shows a note whenever a count isn't 0. */
data class SalesDataGaps(
    val quarantinedBillCount: Int,
    val linelessBillCount: Int,
    val outdatedBillCount: Int,
    val paymentMismatchBillCount: Int,
)
