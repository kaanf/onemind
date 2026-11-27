package com.onemind.staff.data

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.staff.domain.StaffDataGaps
import com.onemind.staff.domain.StaffMember
import com.onemind.staff.domain.StaffSummary
import com.onemind.staff.domain.StaffTotals
import com.onemind.core.api.model.DataStatus as DataStatusDto
import com.onemind.core.api.model.Money as MoneyDto
import com.onemind.core.api.model.StaffSales as StaffSalesDto
import com.onemind.core.api.model.StaffSummary as StaffSummaryDto
import com.onemind.core.api.model.StaffTotals as StaffTotalsDto

/**
 * The domain summary, never a guessed figure: a bad currency code is unreadable (`SERIALIZATION`); a data
 * status this app doesn't know asks for an app update (`NEEDS_APP_UPDATE`).
 */
internal fun StaffSummaryDto.toDomainResult(): Result<StaffSummary, DataError> =
    try {
        Result.Success(toDomain())
    } catch (_: UnknownStatusException) {
        Result.Failure(DataError.Network.NEEDS_APP_UPDATE)
    } catch (_: IllegalArgumentException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    }

private class UnknownStatusException : Exception()

internal fun StaffSummaryDto.toDomain(): StaffSummary =
    StaffSummary(
        hasData =
            when (status) {
                DataStatusDto.READY -> true
                DataStatusDto.NO_DATA_YET -> false
                DataStatusDto.UNKNOWN -> throw UnknownStatusException()
            },
        currency = Currency(currency),
        businessDay = businessDay,
        pendingVenueCount = pendingVenueIds.size,
        totals = totals?.toDomain(),
        staff = staff.orEmpty().map { it.toDomain() },
        dataGaps =
            StaffDataGaps(
                quarantinedBillCount = dataGaps.quarantinedBillCount,
                outdatedBillCount = dataGaps.outdatedBillCount,
                paymentMismatchBillCount = dataGaps.paymentMismatchBillCount,
            ),
    )

private fun MoneyDto.toDomain() = Money(amountMinor, Currency(currency))

private fun StaffTotalsDto.toDomain() =
    StaffTotals(sales = sales.toDomain(), tips = tips.toDomain(), guests = guests, billCount = billCount)

private fun StaffSalesDto.toDomain() =
    StaffMember(
        staffId = staffId,
        name = name,
        sales = sales.toDomain(),
        tips = tips.toDomain(),
        guests = guests,
        billCount = billCount,
        deletedBillCount = deletedBillCount,
    )
