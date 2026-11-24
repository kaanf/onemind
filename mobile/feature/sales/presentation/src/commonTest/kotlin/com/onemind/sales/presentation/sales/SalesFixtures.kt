package com.onemind.sales.presentation.sales

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.sales.domain.PaymentSplit
import com.onemind.sales.domain.SalesComparison
import com.onemind.sales.domain.SalesDataGaps
import com.onemind.sales.domain.SalesDataStatus
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRange
import com.onemind.sales.domain.SalesSummary
import com.onemind.sales.domain.SalesVenue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal object SalesFixtures {
    const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
    const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"
    val prague = TimeZone.of("Europe/Prague")

    fun czk(major: Long) = Money(major * 100, Currency.CZK)

    /** Formatted money as `formatMoney` writes it, `·` = U+202F grouping, `_` = U+00A0 (MoneyFormatTest). */
    fun shown(pattern: String) = pattern.replace('·', ' ').replace('_', ' ')

    val week =
        SalesSummary(
            status = SalesDataStatus.READY,
            currency = Currency.CZK,
            range = SalesRange(SalesPeriod.WEEK, LocalDate(2026, 11, 2), LocalDate(2026, 11, 8), hour = null, finished = false),
            venues =
                listOf(
                    SalesVenue(VENUE_A, LocalDate(2026, 11, 6), prague, SalesDataStatus.READY, Instant.parse("2026-11-06T20:38:00Z")),
                ),
            pendingVenueIds = emptyList(),
            revenue = czk(312_480),
            comparison =
                SalesComparison(
                    previousFrom = LocalDate(2026, 10, 26),
                    previousTo = LocalDate(2026, 11, 1),
                    upToSameTime = true,
                    current = czk(312_480),
                    previousRevenue = czk(289_060),
                    deltaPercent = DecimalValue.parse("8.1"),
                ),
            guests = 1846,
            averageBill = czk(412),
            perGuest = czk(169),
            tips = czk(15_200),
            byPayment = PaymentSplit(czk(212_490), czk(99_990), czk(0), cardPercent = 68),
            possibleLoss = czk(22_410),
            dataGaps = SalesDataGaps(0, 0, 0, 0),
        )

    val noDataYet =
        week.copy(
            status = SalesDataStatus.NO_DATA_YET,
            venues = listOf(SalesVenue(VENUE_A, LocalDate(2026, 11, 6), prague, SalesDataStatus.NO_DATA_YET, null)),
            pendingVenueIds = listOf(VENUE_A),
            revenue = null,
            comparison = null,
            guests = null,
            averageBill = null,
            perGuest = null,
            tips = null,
            byPayment = null,
            possibleLoss = null,
        )
}
