package com.onemind.today.domain

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Screen 01 for one or more venues (`GET /dashboard/today`, `docs/domain/METRICS.md`). Every figure is
 * `null` while no requested venue has data ([DataStatus.NO_DATA_YET]), never 0 (product rule 2); a
 * venue with data and no sales has real zeros. Figures the POS can't give are in [unavailable] and
 * have no value at all.
 */
data class TodaySummary(
    val status: DataStatus,
    val currency: Currency,
    val venues: List<TodayVenue>,
    val pendingVenueIds: List<String>,
    val paidSoFar: Money?,
    val comparison: TodayComparison?,
    val tips: Money?,
    val guests: Int?,
    val perGuest: Money?,
    val possibleLoss: PossibleLoss?,
    val hourly: TodayHourly?,
    val unavailable: Set<UnavailableFigure>,
    val dataGaps: DataGaps,
)

/** `READY`: synced at least once, figures are real (zeros included). `NO_DATA_YET`: waiting for the first sync. */
enum class DataStatus { READY, NO_DATA_YET }

data class TodayVenue(
    val venueId: String,
    /** The venue's current business day (its own time zone and cut-off; rule 6). */
    val businessDay: LocalDate,
    val timeZone: TimeZone,
    val status: DataStatus,
    /** Last bill sync; `null` before the first one. */
    val dataAsOf: Instant?,
)

/** Paid so far against the same weekday last week up to the same local time, over [venueIds] only. */
data class TodayComparison(
    val previousBusinessDay: LocalDate,
    val venueIds: List<String>,
    val current: Money,
    val previous: Money,
    /** One decimal (`12.4`, `-3.0`); `null` when [previous] is 0. */
    val deltaPercent: DecimalValue?,
)

data class PossibleLoss(
    val total: Money,
    val components: List<LossComponent>,
)

data class LossComponent(
    val kind: LossKind,
    val amount: Money,
)

/** First-release `possibleLoss` components (METRICS, Discounts and losses). */
enum class LossKind { DISCOUNTS, REFUNDS, DELETED_BILLS }

/** The hourly chart, in business-day order from the cut-off. */
data class TodayHourly(
    val currentHour: Int,
    val hours: List<HourlyBar>,
)

data class HourlyBar(
    /** Local clock hour, 0–23. */
    val hour: Int,
    val kind: HourKind,
    /** `null` only for a forecast without any earlier same weekday. */
    val amount: Money?,
)

/** `PAST`: paid in that hour. `CURRENT`: paid so far this hour. `FORECAST`: last 4 same weekdays' average. */
enum class HourKind { PAST, CURRENT, FORECAST }

/** A figure of the design the POS can't give (`docs/product/MVP_SCOPE.md`); never shown with a value. */
enum class UnavailableFigure { OPEN_TABS, OCCUPANCY_NOW, STAFF_ON_SHIFT, TIPS_CARD_SHARE }

/** What the figures are missing (rule 2); the screen notes every count above 0. */
data class DataGaps(
    /** Bills that couldn't be read at all; missing from every figure. */
    val quarantinedBillCount: Int,
    /** Today's bills stored without lines; missing from product figures. */
    val linelessBillCount: Int,
    /** Today's bills whose newer version couldn't be read; may be out of date. */
    val outdatedBillCount: Int,
    /** Today's bills whose payments don't add up; left out of paid so far. */
    val paymentMismatchBillCount: Int,
) {
    val isEmpty: Boolean
        get() = quarantinedBillCount == 0 && linelessBillCount == 0 && outdatedBillCount == 0 && paymentMismatchBillCount == 0
}
