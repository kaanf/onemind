package com.onemind.backend.reporting.web

import com.onemind.api.v1.model.DataGaps
import com.onemind.api.v1.model.DataStatus
import com.onemind.api.v1.model.HourlyBar
import com.onemind.api.v1.model.LossComponent
import com.onemind.api.v1.model.Money
import com.onemind.api.v1.model.PossibleLoss
import com.onemind.api.v1.model.TodayComparison
import com.onemind.api.v1.model.TodayHourly
import com.onemind.api.v1.model.TodaySummary
import com.onemind.api.v1.model.TodayVenue
import com.onemind.api.v1.model.UnavailableFigure
import com.onemind.api.v1.web.DashboardApi
import com.onemind.backend.common.security.CurrentUser
import com.onemind.backend.common.web.ETagResponses
import com.onemind.backend.reporting.application.GetTodaySummary
import com.onemind.backend.reporting.application.TodayView
import com.onemind.backend.reporting.domain.BarKind
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset
import java.util.UUID

@RestController
class DashboardController(
    private val getTodaySummary: GetTodaySummary,
    private val eTags: ETagResponses,
) : DashboardApi {
    override fun getToday(
        venueIds: List<UUID>,
        ifNoneMatch: String?,
    ): ResponseEntity<TodaySummary> = eTags.ok(getTodaySummary.get(CurrentUser.id(), venueIds).toDto(), ifNoneMatch)

    private fun TodayView.toDto(): TodaySummary {
        val code = currency.currencyCode

        fun money(minor: Long) = Money(minor, code)
        val figures = figures
        return TodaySummary(
            status = if (figures != null) DataStatus.READY else DataStatus.NO_DATA_YET,
            currency = code,
            venues =
                venues.map {
                    TodayVenue(
                        venueId = it.venueId,
                        businessDay = it.businessDay,
                        timeZone = it.timeZone,
                        status = if (it.ready) DataStatus.READY else DataStatus.NO_DATA_YET,
                        dataAsOf = it.dataAsOf?.atOffset(ZoneOffset.UTC),
                    )
                },
            pendingVenueIds = venues.filterNot { it.ready }.map { it.venueId },
            // The POS can't give these (MVP_SCOPE, data sources): the app shows an empty state or hides them.
            unavailable = UNAVAILABLE,
            dataGaps =
                DataGaps(
                    quarantinedBillCount = gaps.quarantinedBills,
                    linelessBillCount = gaps.billsWithoutLines,
                    outdatedBillCount = gaps.outdatedBills,
                    paymentMismatchBillCount = gaps.paymentMismatchBills,
                ),
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
            possibleLoss =
                figures?.loss?.let { loss ->
                    PossibleLoss(
                        total = money(loss.totalMinor),
                        components =
                            listOf(
                                LossComponent(LossComponent.Kind.DISCOUNTS, money(loss.discountsMinor)),
                                LossComponent(LossComponent.Kind.REFUNDS, money(loss.refundsMinor)),
                                LossComponent(LossComponent.Kind.DELETED_BILLS, money(loss.deletedBillsMinor)),
                            ),
                    )
                },
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
