package com.onemind.sales.data

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.domain.PaymentSplit
import com.onemind.sales.domain.SalesComparison
import com.onemind.sales.domain.SalesDataGaps
import com.onemind.sales.domain.SalesDataStatus
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRange
import com.onemind.sales.domain.SalesSummary
import com.onemind.sales.domain.SalesVenue
import kotlinx.datetime.TimeZone
import com.onemind.core.api.model.DataGaps as DataGapsDto
import com.onemind.core.api.model.DataStatus as DataStatusDto
import com.onemind.core.api.model.Money as MoneyDto
import com.onemind.core.api.model.PaymentSplit as PaymentSplitDto
import com.onemind.core.api.model.SalesComparison as SalesComparisonDto
import com.onemind.core.api.model.SalesRange as SalesRangeDto
import com.onemind.core.api.model.SalesSummary as SalesSummaryDto
import com.onemind.core.api.model.TodayVenue as TodayVenueDto

/**
 * The answer as a domain summary. A data status or period this app doesn't know could show a wrong
 * figure, so it asks for an app update (`NEEDS_APP_UPDATE`); an unknown time zone is unreadable.
 */
internal fun SalesSummaryDto.toDomainResult(): Result<SalesSummary, DataError> =
    try {
        Result.Success(toDomain())
    } catch (_: UnknownValueException) {
        Result.Failure(DataError.Network.NEEDS_APP_UPDATE)
    } catch (_: IllegalArgumentException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    }

private class UnknownValueException : Exception()

internal fun SalesSummaryDto.toDomain(): SalesSummary =
    SalesSummary(
        status = status.toDomain(),
        currency = Currency(currency),
        range = range.toDomain(),
        venues = venues.map { it.toDomain() },
        pendingVenueIds = pendingVenueIds,
        revenue = revenue?.toDomain(),
        comparison = comparison?.toDomain(),
        guests = guests,
        averageBill = averageBill?.toDomain(),
        perGuest = perGuest?.toDomain(),
        tips = tips?.toDomain(),
        byPayment = byPayment?.toDomain(),
        possibleLoss = possibleLoss?.total?.toDomain(),
        dataGaps = dataGaps.toDomain(),
    )

private fun MoneyDto.toDomain() = Money(amountMinor, Currency(currency))

private fun DataStatusDto.toDomain() =
    when (this) {
        DataStatusDto.READY -> SalesDataStatus.READY
        DataStatusDto.NO_DATA_YET -> SalesDataStatus.NO_DATA_YET
        DataStatusDto.UNKNOWN -> throw UnknownValueException()
    }

private fun SalesRangeDto.toDomain() =
    SalesRange(
        period =
            when (period) {
                SalesRangeDto.Period.HOUR -> SalesPeriod.HOUR
                SalesRangeDto.Period.DAY -> SalesPeriod.DAY
                SalesRangeDto.Period.WEEK -> SalesPeriod.WEEK
                SalesRangeDto.Period.MONTH -> SalesPeriod.MONTH
                SalesRangeDto.Period.UNKNOWN -> throw UnknownValueException()
            },
        from = from,
        to = to,
        hour = hour,
        finished = finished,
    )

private fun TodayVenueDto.toDomain() =
    SalesVenue(
        venueId = venueId,
        businessDay = businessDay,
        timeZone = TimeZone.of(timeZone),
        status = status.toDomain(),
        dataAsOf = dataAsOf,
    )

private fun SalesComparisonDto.toDomain() =
    SalesComparison(
        previousFrom = previous.from,
        previousTo = previous.to,
        upToSameTime = upToSameTime,
        current = current.toDomain(),
        previousRevenue = previousRevenue.toDomain(),
        deltaPercent = deltaPercent?.let(DecimalValue::parse),
    )

private fun PaymentSplitDto.toDomain() = PaymentSplit(card.toDomain(), cash.toDomain(), other.toDomain(), cardPercent)

private fun DataGapsDto.toDomain() =
    SalesDataGaps(
        quarantinedBillCount = quarantinedBillCount,
        linelessBillCount = linelessBillCount,
        outdatedBillCount = outdatedBillCount,
        paymentMismatchBillCount = paymentMismatchBillCount,
    )
