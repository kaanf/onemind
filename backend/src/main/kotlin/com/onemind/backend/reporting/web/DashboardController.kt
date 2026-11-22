package com.onemind.backend.reporting.web

import com.onemind.api.v1.model.DataGaps
import com.onemind.api.v1.model.DataStatus
import com.onemind.api.v1.model.DaySpan
import com.onemind.api.v1.model.DiscountTypeLoss
import com.onemind.api.v1.model.HourlyBar
import com.onemind.api.v1.model.LossComparison
import com.onemind.api.v1.model.LossComponent
import com.onemind.api.v1.model.LossRecords
import com.onemind.api.v1.model.LossesSummary
import com.onemind.api.v1.model.Money
import com.onemind.api.v1.model.PaymentSplit
import com.onemind.api.v1.model.PossibleLoss
import com.onemind.api.v1.model.SalesComparison
import com.onemind.api.v1.model.SalesRange
import com.onemind.api.v1.model.SalesSummary
import com.onemind.api.v1.model.TodayComparison
import com.onemind.api.v1.model.TodayHourly
import com.onemind.api.v1.model.TodaySummary
import com.onemind.api.v1.model.TodayVenue
import com.onemind.api.v1.model.UnavailableFigure
import com.onemind.api.v1.model.UnavailableLossComponent
import com.onemind.api.v1.web.DashboardApi
import com.onemind.backend.common.security.CurrentUser
import com.onemind.backend.common.web.ETagResponses
import com.onemind.backend.reporting.application.GetLosses
import com.onemind.backend.reporting.application.GetSales
import com.onemind.backend.reporting.application.GetTodaySummary
import com.onemind.backend.reporting.application.LossesView
import com.onemind.backend.reporting.application.SalesPeriodUnknownException
import com.onemind.backend.reporting.application.SalesView
import com.onemind.backend.reporting.application.ScopedVenue
import com.onemind.backend.reporting.application.TodayGapsView
import com.onemind.backend.reporting.application.TodayView
import com.onemind.backend.reporting.domain.BarKind
import com.onemind.backend.reporting.domain.LossTotals
import com.onemind.backend.reporting.domain.SalesPeriod
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

@RestController
class DashboardController(
    private val getTodaySummary: GetTodaySummary,
    private val getSales: GetSales,
    private val getLosses: GetLosses,
    private val eTags: ETagResponses,
) : DashboardApi {
    override fun getToday(
        venueIds: List<UUID>,
        ifNoneMatch: String?,
    ): ResponseEntity<TodaySummary> = eTags.ok(getTodaySummary.get(CurrentUser.id(), venueIds).toDto(), ifNoneMatch)

    override fun getSales(
        venueIds: List<UUID>,
        period: String,
        date: LocalDate?,
        ifNoneMatch: String?,
    ): ResponseEntity<SalesSummary> {
        val salesPeriod = SalesPeriod.entries.firstOrNull { it.name == period } ?: throw SalesPeriodUnknownException()
        return eTags.ok(getSales.get(CurrentUser.id(), venueIds, salesPeriod, date).toDto(), ifNoneMatch)
    }

    override fun getLosses(
        venueIds: List<UUID>,
        period: String,
        date: LocalDate?,
        ifNoneMatch: String?,
    ): ResponseEntity<LossesSummary> {
        val salesPeriod = SalesPeriod.entries.firstOrNull { it.name == period } ?: throw SalesPeriodUnknownException()
        return eTags.ok(getLosses.get(CurrentUser.id(), venueIds, salesPeriod, date).toDto(), ifNoneMatch)
    }

    private fun LossesView.toDto(): LossesSummary {
        val code = currency.currencyCode

        fun money(minor: Long) = Money(minor, code)
        val figures = figures
        return LossesSummary(
            status = if (figures != null) DataStatus.READY else DataStatus.NO_DATA_YET,
            currency = code,
            range = SalesRange(SalesRange.Period.valueOf(ranges.period.name), ranges.current.from, ranges.current.to, ranges.finished),
            venues = venues.map { it.toDto(businessDay, timeZone) },
            pendingVenueIds = venues.filterNot { it.ready }.map { it.venueId },
            discountsByType = figures?.discountsByType?.map { DiscountTypeLoss(it.type, money(it.amountMinor), it.records) }.orEmpty(),
            unavailableComponents =
                listOf(
                    UnavailableLossComponent.UNPAID_CLOSED,
                    UnavailableLossComponent.ITEM_VOIDS,
                    UnavailableLossComponent.STOCK_CORRECTIONS,
                ),
            dataGaps = gaps.toDto(discountMismatchDays = figures?.discountMismatches?.size),
            possibleLoss = figures?.loss?.toDto(currency),
            sharePercent = figures?.sharePercent?.oneDecimal(),
            records = figures?.records,
            comparison =
                comparison?.let {
                    LossComparison(
                        previous = DaySpan(ranges.previous.from, ranges.previous.to),
                        compared = DaySpan(it.from, it.to),
                        upToSameTime = it.upToSameTime,
                        venueIds = it.venueIds,
                        currentSharePercent = it.currentSharePercent?.oneDecimal(),
                        previousSharePercent = it.previousSharePercent?.oneDecimal(),
                        changePoints = it.changePoints?.toPlainString(),
                    )
                },
            refunds = figures?.refunds?.let { LossRecords(money(it.amountMinor), it.records) },
            deletedBills = figures?.deletedBills?.let { LossRecords(money(it.amountMinor), it.records) },
        )
    }

    private fun BigDecimal.oneDecimal(): String = setScale(1, RoundingMode.HALF_UP).toPlainString()

    private fun SalesView.toDto(): SalesSummary {
        val code = currency.currencyCode

        fun money(minor: Long) = Money(minor, code)
        val figures = figures
        return SalesSummary(
            status = if (figures != null) DataStatus.READY else DataStatus.NO_DATA_YET,
            currency = code,
            range =
                SalesRange(
                    period = SalesRange.Period.valueOf(ranges.period.name),
                    from = ranges.current.from,
                    to = ranges.current.to,
                    finished = ranges.finished,
                    hour = hour,
                ),
            venues = venues.map { it.toDto(businessDay, timeZone) },
            pendingVenueIds = venues.filterNot { it.ready }.map { it.venueId },
            unavailable = listOf(UnavailableFigure.TIPS_CARD_SHARE),
            dataGaps = gaps.toDto(),
            revenue = figures?.let { money(it.revenueMinor) },
            comparison =
                comparison?.let {
                    SalesComparison(
                        previous = DaySpan(ranges.previous.from, ranges.previous.to),
                        compared = DaySpan(it.from, it.to),
                        upToSameTime = it.upToSameTime,
                        venueIds = it.venueIds,
                        current = money(it.currentMinor),
                        previousRevenue = money(it.previousMinor),
                        deltaPercent = it.deltaPercent?.toPlainString(),
                    )
                },
            guests = figures?.guests,
            averageBill = figures?.averageBillMinor?.let(::money),
            perGuest = figures?.perGuestMinor?.let(::money),
            tips = figures?.let { money(it.tipsMinor) },
            byPayment =
                figures?.payments?.let {
                    PaymentSplit(
                        card = money(it.cardMinor),
                        cash = money(it.cashMinor),
                        other = money(it.otherMinor),
                        cardPercent = it.cardPercent,
                    )
                },
            possibleLoss = figures?.loss?.toDto(currency),
        )
    }

    private fun TodayView.toDto(): TodaySummary {
        val code = currency.currencyCode

        fun money(minor: Long) = Money(minor, code)
        val figures = figures
        return TodaySummary(
            status = if (figures != null) DataStatus.READY else DataStatus.NO_DATA_YET,
            currency = code,
            venues = venues.map { ScopedVenue(it.venueId, it.dataAsOf).toDto(it.businessDay, it.timeZone) },
            pendingVenueIds = venues.filterNot { it.ready }.map { it.venueId },
            // The POS can't give these (MVP_SCOPE, data sources): the app shows an empty state or hides them.
            unavailable = UNAVAILABLE,
            dataGaps = gaps.toDto(),
            paidSoFar = figures?.let { money(it.paidMinor) },
            comparison =
                comparison?.let {
                    TodayComparison(
                        previousBusinessDay = it.previousBusinessDay,
                        venueIds = it.venueIds,
                        current = money(it.currentMinor),
                        previous = money(it.previousMinor),
                        deltaPercent = it.deltaPercent?.toPlainString(),
                    )
                },
            tips = figures?.let { money(it.tipsMinor) },
            guests = figures?.guests,
            perGuest = figures?.perGuestMinor?.let(::money),
            possibleLoss = figures?.loss?.toDto(currency),
            hourly =
                figures?.let { f ->
                    TodayHourly(
                        currentHour = currentHour,
                        hours =
                            f.bars.map { bar ->
                                HourlyBar(
                                    hour = bar.hour,
                                    kind =
                                        when (bar.kind) {
                                            BarKind.PAST -> HourlyBar.Kind.PAST
                                            BarKind.CURRENT -> HourlyBar.Kind.CURRENT
                                            BarKind.FORECAST -> HourlyBar.Kind.FORECAST
                                        },
                                    amount = bar.amountMinor?.let(::money),
                                )
                            },
                    )
                },
        )
    }

    private fun ScopedVenue.toDto(
        businessDay: LocalDate,
        timeZone: String,
    ) = TodayVenue(
        venueId = venueId,
        businessDay = businessDay,
        timeZone = timeZone,
        status = if (ready) DataStatus.READY else DataStatus.NO_DATA_YET,
        dataAsOf = dataAsOf?.atOffset(ZoneOffset.UTC),
    )

    private fun TodayGapsView.toDto(discountMismatchDays: Int? = null) =
        DataGaps(
            quarantinedBillCount = quarantinedBills,
            linelessBillCount = billsWithoutLines,
            outdatedBillCount = outdatedBills,
            paymentMismatchBillCount = paymentMismatchBills,
            discountMismatchDayCount = discountMismatchDays,
        )

    private fun LossTotals.toDto(currency: Currency): PossibleLoss {
        fun money(minor: Long) = Money(minor, currency.currencyCode)
        return PossibleLoss(
            total = money(totalMinor),
            components =
                listOf(
                    LossComponent(LossComponent.Kind.DISCOUNTS, money(discountsMinor)),
                    LossComponent(LossComponent.Kind.REFUNDS, money(refundsMinor)),
                    LossComponent(LossComponent.Kind.DELETED_BILLS, money(deletedBillsMinor)),
                ),
        )
    }

    private companion object {
        val UNAVAILABLE =
            listOf(
                UnavailableFigure.OPEN_TABS,
                UnavailableFigure.OCCUPANCY_NOW,
                UnavailableFigure.STAFF_ON_SHIFT,
                UnavailableFigure.TIPS_CARD_SHARE,
            )
    }
}
