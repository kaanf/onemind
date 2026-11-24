package com.onemind.sales.domain.products

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import kotlinx.datetime.LocalDate

/**
 * Products of one business day (`docs/domain/METRICS.md`, products and hours). Lists are empty when no
 * requested venue has data for the day ([hasData] false): no figure is shown then (rule 2).
 */
data class ProductsSummary(
    /** `false` while no requested venue has data for the day (`NO_DATA_YET`). */
    val hasData: Boolean,
    val currency: Currency,
    val businessDay: LocalDate,
    /** Venues without data for the day, left out of the figures. */
    val pendingVenueCount: Int,
    /** The day's local hours with product sales, in business-day order (e.g. 16 … 23, 0, 1). */
    val hours: List<Int>,
    /** Categories of the listed products and their ancestors. */
    val categories: List<ProductCategory>,
    /** Largest gross first. Products of different venues are never merged. */
    val products: List<ProductSales>,
    val dataGaps: ProductsDataGaps,
)

data class ProductCategory(
    val id: String,
    val name: String,
    val parentId: String?,
)

data class ProductSales(
    val venueId: String,
    val productId: String,
    val name: String,
    val categoryId: String?,
    /** Quantity sold ("12", "1.5"). */
    val sold: DecimalValue,
    /** Sum of line totals. */
    val gross: Money,
    /** Hours with a quantity, in business-day order. */
    val hourly: List<HourQuantity>,
    val peakHour: Int?,
)

data class HourQuantity(
    val hour: Int,
    val quantity: DecimalValue,
)

/** Bills missing from these figures (`DataGaps` in the API). */
data class ProductsDataGaps(
    val quarantinedBillCount: Int,
    val linelessBillCount: Int,
    val outdatedBillCount: Int,
) {
    val isEmpty: Boolean get() = quarantinedBillCount == 0 && linelessBillCount == 0 && outdatedBillCount == 0
}
