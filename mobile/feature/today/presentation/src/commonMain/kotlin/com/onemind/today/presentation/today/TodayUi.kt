package com.onemind.today.presentation.today

import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.presentation.format.VenueLocale
import com.onemind.core.presentation.format.formatClockTime
import com.onemind.core.presentation.format.formatDeltaPercent
import com.onemind.core.presentation.format.formatMoney
import com.onemind.today.domain.DataStatus
import com.onemind.today.domain.HourKind
import com.onemind.today.domain.LossKind
import com.onemind.today.domain.TodayHourly
import com.onemind.today.domain.TodaySummary
import com.onemind.today.domain.UnavailableFigure
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Screen 01 ready to draw: amounts formatted in the venue's locale, times in the venue's time zone.
 * A `null` figure is drawn as an empty value, never as 0 (rule 2). Words (weekdays, months, labels)
 * stay typed here and are resolved from resources by the screen.
 */
data class TodayUi(
    /** The venue's business day: after midnight it is still the evening's date (rule 6). */
    val businessDay: LocalDate?,
    /** Wall-clock time now in the venue's time zone (*21:40*). */
    val clockTime: String?,
    /** Oldest last sync of the venues with data (*21:38*), ADR 0005. */
    val updatedAt: String?,
    val waitingForFirstSync: Boolean,
    val pendingVenueCount: Int,
    val paidSoFar: String?,
    val comparison: ComparisonUi?,
    /** `Open tabs` is shown only as its empty-state card; the API sends no value for it. */
    val openTabsUnavailable: Boolean,
    val tips: String?,
    val hourly: HourlyUi?,
    val occupancyUnavailable: Boolean,
    val guests: String?,
    val perGuest: String?,
    val possibleLoss: PossibleLossUi?,
    val dataGaps: List<DataGapLine>,
)

data class ComparisonUi(
    /** `+12.4%`. */
    val delta: String,
    val tone: BadgeTone,
    val previousWeekday: DayOfWeek,
)

data class HourlyUi(
    val bars: List<HourBarUi>,
    /** Hour with the most revenue paid so far (past and current), for the spoken description. */
    val peakHour: Int?,
)

data class HourBarUi(
    val hour: Int,
    val kind: HourKind,
    /** Bar height against the day's largest bar, 0–1; 0 for a forecast without any earlier weekday. */
    val heightFraction: Float,
)

data class PossibleLossUi(
    val total: String,
    /** The components it includes, named on screen (first release: no item voids, no stock). */
    val includes: List<LossKind>,
)

/** One line of the "figures are missing bills" note; [count] is above 0. */
data class DataGapLine(
    val kind: DataGapKind,
    val count: Int,
)

enum class DataGapKind { UNREADABLE, WITHOUT_ITEMS, OUTDATED, PAYMENT_MISMATCH }

/** Maps the API answer to [TodayUi] at [now]. Pure, so the screen rules are tested without Compose. */
fun TodaySummary.toUi(now: Instant): TodayUi {
    val locale = VenueLocale.forCurrency(currency)
    // Venues on one Today share their time zone and cut-off (422 BUSINESS_DAYS_DIFFER otherwise).
    val venue = venues.firstOrNull()
    val ready = status == DataStatus.READY
    return TodayUi(
        businessDay = venue?.businessDay,
        clockTime = venue?.let { formatClockTime(now, it.timeZone) },
        updatedAt =
            venues
                .filter { it.status == DataStatus.READY }
                .mapNotNull { v -> v.dataAsOf?.let { it to v.timeZone } }
                .minByOrNull { it.first }
                ?.let { (instant, zone) -> formatClockTime(instant, zone) },
        waitingForFirstSync = !ready,
        pendingVenueCount = if (ready) pendingVenueIds.size else 0,
        paidSoFar = paidSoFar?.let { formatMoney(it, locale) },
        comparison =
            comparison?.let { c ->
                c.deltaPercent?.let { delta ->
                    ComparisonUi(
                        delta = formatDeltaPercent(delta),
                        tone =
                            when {
                                delta.isZero -> BadgeTone.Neutral
                                delta.isNegative -> BadgeTone.Negative
                                else -> BadgeTone.Positive
                            },
                        previousWeekday = c.previousBusinessDay.dayOfWeek,
                    )
                }
            },
        openTabsUnavailable = UnavailableFigure.OPEN_TABS in unavailable,
        tips = tips?.let { formatMoney(it, locale) },
        hourly = hourly?.toUi(),
        occupancyUnavailable = UnavailableFigure.OCCUPANCY_NOW in unavailable,
        guests = guests?.toString(),
        perGuest = perGuest?.let { formatMoney(it, locale) },
        possibleLoss =
            possibleLoss?.let { loss ->
                PossibleLossUi(formatMoney(loss.total, locale), loss.components.map { it.kind }.distinct())
            },
        dataGaps =
            listOf(
                DataGapLine(DataGapKind.UNREADABLE, dataGaps.quarantinedBillCount),
                DataGapLine(DataGapKind.WITHOUT_ITEMS, dataGaps.linelessBillCount),
                DataGapLine(DataGapKind.OUTDATED, dataGaps.outdatedBillCount),
                DataGapLine(DataGapKind.PAYMENT_MISMATCH, dataGaps.paymentMismatchBillCount),
            ).filter { it.count > 0 },
    )
}

private fun TodayHourly.toUi(): HourlyUi {
    val largest = hours.maxOfOrNull { it.amount?.amountMinor ?: 0L }?.takeIf { it > 0 }
    return HourlyUi(
        bars =
            hours.map { bar ->
                val minor = bar.amount?.amountMinor ?: 0L
                HourBarUi(
                    hour = bar.hour,
                    kind = bar.kind,
                    heightFraction = if (largest == null || minor <= 0) 0f else minor.toFloat() / largest.toFloat(),
                )
            },
        peakHour =
            hours
                .filter { it.kind != HourKind.FORECAST && (it.amount?.amountMinor ?: 0L) > 0 }
                .maxByOrNull { it.amount!!.amountMinor }
                ?.hour,
    )
}
