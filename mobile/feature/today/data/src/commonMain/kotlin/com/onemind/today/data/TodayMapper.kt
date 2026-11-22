package com.onemind.today.data

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
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
import kotlinx.datetime.TimeZone
import com.onemind.core.api.model.DataGaps as DataGapsDto
import com.onemind.core.api.model.DataStatus as DataStatusDto
import com.onemind.core.api.model.HourlyBar as HourlyBarDto
import com.onemind.core.api.model.LossComponent as LossComponentDto
import com.onemind.core.api.model.Money as MoneyDto
import com.onemind.core.api.model.PossibleLoss as PossibleLossDto
import com.onemind.core.api.model.TodayComparison as TodayComparisonDto
import com.onemind.core.api.model.TodayHourly as TodayHourlyDto
import com.onemind.core.api.model.TodaySummary as TodaySummaryDto
import com.onemind.core.api.model.TodayVenue as TodayVenueDto
import com.onemind.core.api.model.UnavailableFigure as UnavailableFigureDto

/**
 * The domain summary, never a guessed figure:
 * - an answer that breaks the contract (a bad currency code, time zone or decimal) is unreadable
 *   (`SERIALIZATION`);
 * - a data status this app doesn't know asks for an app update (`NEEDS_APP_UPDATE`): it decides whether
 *   figures are real;
 * - other values added to the API later (`UNKNOWN`) are kept safe: an unavailable figure the app doesn't
 *   draw anyway is left out, an hour of an unknown kind keeps an empty slot, and an unknown loss
 *   component becomes [LossKind.OTHER] (it is in the server's total).
 */
internal fun TodaySummaryDto.toDomainResult(): Result<TodaySummary, DataError> =
    try {
        Result.Success(toDomain())
    } catch (_: UnknownStatusException) {
        Result.Failure(DataError.Network.NEEDS_APP_UPDATE)
    } catch (_: IllegalArgumentException) {
        // Currency, DecimalValue.parse and TimeZone.of (IllegalTimeZoneException) reject with it.
        Result.Failure(DataError.Network.SERIALIZATION)
    }

private class UnknownStatusException : Exception()

internal fun TodaySummaryDto.toDomain(): TodaySummary =
    TodaySummary(
        status = status.toDomain(),
        currency = Currency(currency),
        venues = venues.map { it.toDomain() },
        pendingVenueIds = pendingVenueIds,
        paidSoFar = paidSoFar?.toDomain(),
        comparison = comparison?.toDomain(),
        tips = tips?.toDomain(),
        guests = guests,
        perGuest = perGuest?.toDomain(),
        possibleLoss = possibleLoss?.toDomain(),
        hourly = hourly?.toDomain(),
        unavailable = unavailable.mapNotNullTo(mutableSetOf()) { it.toDomain() },
        dataGaps = dataGaps.toDomain(),
    )

private fun MoneyDto.toDomain() = Money(amountMinor, Currency(currency))

private fun DataStatusDto.toDomain() =
    when (this) {
        DataStatusDto.READY -> DataStatus.READY

        DataStatusDto.NO_DATA_YET -> DataStatus.NO_DATA_YET

        // Whether figures are real depends on it: never guess.
        DataStatusDto.UNKNOWN -> throw UnknownStatusException()
    }

private fun TodayVenueDto.toDomain() =
    TodayVenue(
        venueId = venueId,
        businessDay = businessDay,
        timeZone = TimeZone.of(timeZone),
        status = status.toDomain(),
        dataAsOf = dataAsOf,
    )

private fun TodayComparisonDto.toDomain() =
    TodayComparison(
        previousBusinessDay = previousBusinessDay,
        venueIds = venueIds,
        current = current.toDomain(),
        previous = previous.toDomain(),
        deltaPercent = deltaPercent?.let(DecimalValue::parse),
    )

private fun PossibleLossDto.toDomain() = PossibleLoss(total.toDomain(), components.map { it.toDomain() })

private fun LossComponentDto.toDomain() =
    LossComponent(
        kind =
            when (kind) {
                LossComponentDto.Kind.DISCOUNTS -> LossKind.DISCOUNTS
                LossComponentDto.Kind.REFUNDS -> LossKind.REFUNDS
                LossComponentDto.Kind.DELETED_BILLS -> LossKind.DELETED_BILLS
                LossComponentDto.Kind.UNKNOWN -> LossKind.OTHER
            },
        amount = amount.toDomain(),
    )

private fun TodayHourlyDto.toDomain() = TodayHourly(currentHour, hours.map { it.toDomain() })

private fun HourlyBarDto.toDomain() =
    HourlyBar(
        hour = hour,
        kind =
            when (kind) {
                HourlyBarDto.Kind.PAST -> HourKind.PAST
                HourlyBarDto.Kind.CURRENT -> HourKind.CURRENT
                HourlyBarDto.Kind.FORECAST -> HourKind.FORECAST
                HourlyBarDto.Kind.UNKNOWN -> HourKind.UNKNOWN
            },
        amount = amount?.toDomain(),
    )

private fun UnavailableFigureDto.toDomain(): UnavailableFigure? =
    when (this) {
        UnavailableFigureDto.OPEN_TABS -> UnavailableFigure.OPEN_TABS
        UnavailableFigureDto.OCCUPANCY_NOW -> UnavailableFigure.OCCUPANCY_NOW
        UnavailableFigureDto.STAFF_ON_SHIFT -> UnavailableFigure.STAFF_ON_SHIFT
        UnavailableFigureDto.TIPS_CARD_SHARE -> UnavailableFigure.TIPS_CARD_SHARE
        UnavailableFigureDto.UNKNOWN -> null
    }

private fun DataGapsDto.toDomain() =
    DataGaps(
        quarantinedBillCount = quarantinedBillCount,
        linelessBillCount = linelessBillCount,
        outdatedBillCount = outdatedBillCount,
        paymentMismatchBillCount = paymentMismatchBillCount,
    )
