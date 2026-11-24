package com.onemind.sales.data.losses

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.domain.losses.DiscountTypeLoss
import com.onemind.sales.domain.losses.LossRecords
import com.onemind.sales.domain.losses.LossesComparison
import com.onemind.sales.domain.losses.LossesDataGaps
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesSummary
import com.onemind.sales.domain.losses.UnavailableLossComponent
import com.onemind.core.api.model.DataGaps as DataGapsDto
import com.onemind.core.api.model.DataStatus as DataStatusDto
import com.onemind.core.api.model.DiscountTypeLoss as DiscountTypeLossDto
import com.onemind.core.api.model.LossComparison as LossComparisonDto
import com.onemind.core.api.model.LossComponent as LossComponentDto
import com.onemind.core.api.model.LossRecords as LossRecordsDto
import com.onemind.core.api.model.LossesSummary as LossesSummaryDto
import com.onemind.core.api.model.Money as MoneyDto
import com.onemind.core.api.model.SalesRange as SalesRangeDto
import com.onemind.core.api.model.UnavailableLossComponent as UnavailableLossComponentDto

/**
 * The domain summary, never a guessed figure:
 * - an answer that breaks the contract (a bad currency code or decimal, an hour period) is unreadable
 *   (`SERIALIZATION`);
 * - a data status this app doesn't know asks for an app update (`NEEDS_APP_UPDATE`): it decides whether
 *   figures are real;
 * - an unavailable component the app doesn't know is left out (it isn't drawn anyway).
 */
internal fun LossesSummaryDto.toDomainResult(): Result<LossesSummary, DataError> =
    try {
        Result.Success(toDomain())
    } catch (_: UnknownStatusException) {
        Result.Failure(DataError.Network.NEEDS_APP_UPDATE)
    } catch (_: IllegalArgumentException) {
        // Currency and DecimalValue.parse reject with it.
        Result.Failure(DataError.Network.SERIALIZATION)
    }

private class UnknownStatusException : Exception()

internal fun LossesSummaryDto.toDomain(): LossesSummary =
    LossesSummary(
        hasData =
            when (status) {
                DataStatusDto.READY -> true
                DataStatusDto.NO_DATA_YET -> false
                DataStatusDto.UNKNOWN -> throw UnknownStatusException()
            },
        currency = Currency(currency),
        period = range.period.toDomain(),
        from = range.from,
        to = range.to,
        finished = range.finished,
        pendingVenueCount = pendingVenueIds.size,
        possibleLoss = possibleLoss?.total?.toDomain(),
        sharePercent = sharePercent?.let(DecimalValue::parse),
        records = records,
        comparison = comparison?.toDomain(),
        discountsByType = discountsByType.map { it.toDomain() },
        discounts =
            possibleLoss
                ?.components
                ?.firstOrNull { it.kind == LossComponentDto.Kind.DISCOUNTS }
                ?.amount
                ?.toDomain(),
        refunds = refunds?.toDomain(),
        deletedBills = deletedBills?.toDomain(),
        unavailable = unavailableComponents.mapNotNullTo(mutableSetOf()) { it.toDomain() },
        dataGaps = dataGaps.toDomain(),
    )

private fun MoneyDto.toDomain() = Money(amountMinor, Currency(currency))

private fun SalesRangeDto.Period.toDomain() =
    when (this) {
        SalesRangeDto.Period.DAY -> LossesPeriod.DAY

        SalesRangeDto.Period.WEEK -> LossesPeriod.WEEK

        SalesRangeDto.Period.MONTH -> LossesPeriod.MONTH

        // Losses never cover an hour; anything else is a period this screen can't label.
        SalesRangeDto.Period.HOUR, SalesRangeDto.Period.UNKNOWN -> throw IllegalArgumentException("period $this")
    }

private fun LossComparisonDto.toDomain() =
    LossesComparison(
        previousFrom = previous.from,
        previousTo = previous.to,
        changePoints = changePoints?.let(DecimalValue::parse),
    )

private fun DiscountTypeLossDto.toDomain() = DiscountTypeLoss(type = type, records = records, amount = amount.toDomain())

private fun LossRecordsDto.toDomain() = LossRecords(records = records, amount = amount.toDomain())

private fun UnavailableLossComponentDto.toDomain(): UnavailableLossComponent? =
    when (this) {
        UnavailableLossComponentDto.UNPAID_CLOSED -> UnavailableLossComponent.UNPAID_CLOSED
        UnavailableLossComponentDto.ITEM_VOIDS -> UnavailableLossComponent.ITEM_VOIDS
        UnavailableLossComponentDto.STOCK_CORRECTIONS -> UnavailableLossComponent.STOCK_CORRECTIONS
        UnavailableLossComponentDto.UNKNOWN -> null
    }

private fun DataGapsDto.toDomain() =
    LossesDataGaps(
        quarantinedBillCount = quarantinedBillCount,
        linelessBillCount = linelessBillCount,
        outdatedBillCount = outdatedBillCount,
        paymentMismatchBillCount = paymentMismatchBillCount,
        discountMismatchDayCount = discountMismatchDayCount ?: 0,
    )
