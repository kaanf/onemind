package com.onemind.today.presentation

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.today.domain.DataGaps
import com.onemind.today.domain.DataStatus
import com.onemind.today.domain.HourKind
import com.onemind.today.domain.HourlyBar
import com.onemind.today.domain.LossComponent
import com.onemind.today.domain.LossKind
import com.onemind.today.domain.PossibleLoss
import com.onemind.today.domain.TodayComparison
import com.onemind.today.domain.TodayHourly
import com.onemind.today.domain.TodaySummary
import com.onemind.today.domain.TodayVenue
import com.onemind.today.domain.UnavailableFigure
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal object TodayFixtures {
    const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
    const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"
    val prague = TimeZone.of("Europe/Prague")

    fun czk(major: Long) = Money(major * 100, Currency.CZK)

    /** Formatted money as `formatMoney` writes it, `·` = U+202F grouping, `_` = U+00A0 (MoneyFormatTest). */
    fun shown(pattern: String) = pattern.replace('·', '\u202F').replace('_', '\u00A0')

    val noGaps = DataGaps(0, 0, 0, 0)

    val ready =
        TodaySummary(
            status = DataStatus.READY,
            currency = Currency.CZK,
            venues = listOf(TodayVenue(VENUE_A, LocalDate(2026, 11, 6), prague, DataStatus.READY, Instant.parse("2026-11-06T20:38:00Z"))),
            pendingVenueIds = emptyList(),
            paidSoFar = czk(48_320),
            comparison = TodayComparison(LocalDate(2026, 10, 30), listOf(VENUE_A), czk(48_320), czk(42_990), DecimalValue.parse("12.4")),
            tips = czk(3_940),
            guests = 286,
            perGuest = czk(169),
            possibleLoss =
                PossibleLoss(
                    czk(1_870),
                    listOf(LossComponent(LossKind.DISCOUNTS, czk(1_500)), LossComponent(LossKind.REFUNDS, czk(370))),
                ),
            hourly =
                TodayHourly(
                    currentHour = 21,
                    hours =
                        listOf(
                            HourlyBar(20, HourKind.PAST, czk(9_100)),
                            HourlyBar(21, HourKind.CURRENT, czk(4_200)),
                            HourlyBar(22, HourKind.FORECAST, czk(18_200)),
                            HourlyBar(23, HourKind.FORECAST, null),
                        ),
                ),
            unavailable =
                setOf(
                    UnavailableFigure.OPEN_TABS,
                    UnavailableFigure.OCCUPANCY_NOW,
                    UnavailableFigure.STAFF_ON_SHIFT,
                    UnavailableFigure.TIPS_CARD_SHARE,
                ),
            dataGaps = noGaps,
        )

    val noDataYet =
        ready.copy(
            status = DataStatus.NO_DATA_YET,
            venues = listOf(TodayVenue(VENUE_A, LocalDate(2026, 11, 6), prague, DataStatus.NO_DATA_YET, null)),
            pendingVenueIds = listOf(VENUE_A),
            paidSoFar = null,
            comparison = null,
            tips = null,
            guests = null,
            perGuest = null,
            possibleLoss = null,
            hourly = null,
        )
}
