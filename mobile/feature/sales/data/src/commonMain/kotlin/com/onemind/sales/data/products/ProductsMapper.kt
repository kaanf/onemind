package com.onemind.sales.data.products

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.domain.products.HourQuantity
import com.onemind.sales.domain.products.ProductCategory
import com.onemind.sales.domain.products.ProductSales
import com.onemind.sales.domain.products.ProductsDataGaps
import com.onemind.sales.domain.products.ProductsSummary
import com.onemind.core.api.model.DataStatus as DataStatusDto
import com.onemind.core.api.model.Money as MoneyDto
import com.onemind.core.api.model.ProductCategory as ProductCategoryDto
import com.onemind.core.api.model.ProductQuantity as ProductQuantityDto
import com.onemind.core.api.model.ProductSales as ProductSalesDto
import com.onemind.core.api.model.ProductsSummary as ProductsSummaryDto

/**
 * The domain summary, never a guessed figure: a bad currency code or decimal is unreadable
 * (`SERIALIZATION`); a data status this app doesn't know asks for an app update (`NEEDS_APP_UPDATE`).
 */
internal fun ProductsSummaryDto.toDomainResult(): Result<ProductsSummary, DataError> =
    try {
        Result.Success(toDomain())
    } catch (_: UnknownStatusException) {
        Result.Failure(DataError.Network.NEEDS_APP_UPDATE)
    } catch (_: IllegalArgumentException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    }

private class UnknownStatusException : Exception()

internal fun ProductsSummaryDto.toDomain(): ProductsSummary =
    ProductsSummary(
        hasData =
            when (status) {
                DataStatusDto.READY -> true
                DataStatusDto.NO_DATA_YET -> false
                DataStatusDto.UNKNOWN -> throw UnknownStatusException()
            },
        currency = Currency(currency),
        businessDay = businessDay,
        pendingVenueCount = pendingVenueIds.size,
        hours = hours.orEmpty(),
        categories = categories.orEmpty().map { it.toDomain() },
        products = products.orEmpty().map { it.toDomain() },
        dataGaps =
            ProductsDataGaps(
                quarantinedBillCount = dataGaps.quarantinedBillCount,
                linelessBillCount = dataGaps.linelessBillCount,
                outdatedBillCount = dataGaps.outdatedBillCount,
            ),
    )

private fun MoneyDto.toDomain() = Money(amountMinor, Currency(currency))

private fun ProductCategoryDto.toDomain() = ProductCategory(id = id, name = name, parentId = parentId)

private fun ProductSalesDto.toDomain() =
    ProductSales(
        venueId = venueId,
        productId = productId,
        name = name,
        categoryId = categoryId,
        sold = DecimalValue.parse(sold),
        gross = gross.toDomain(),
        hourly = hourly.map { it.toDomain() },
        peakHour = peakHour,
    )

private fun ProductQuantityDto.toDomain() = HourQuantity(hour = hour, quantity = DecimalValue.parse(quantity))
