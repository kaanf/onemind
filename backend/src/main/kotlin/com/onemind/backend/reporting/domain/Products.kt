package com.onemind.backend.reporting.domain

import java.math.BigDecimal
import java.util.UUID

/** One stored product hour of a venue's day (`reporting.product_hourly_sales`), by bill opening hour. */
data class ProductHour(
    val productId: UUID,
    val hour: Int,
    val quantity: BigDecimal,
    val grossMinor: Long,
)

data class ProductName(
    val name: String,
    val categoryId: UUID?,
)

data class MenuCategory(
    val categoryId: UUID,
    val name: String,
    val parentId: UUID?,
)

/** One venue's input to Products by hour: its product hours of the day and the menu names it needs. */
data class VenueProducts(
    val venueId: UUID,
    val hours: List<ProductHour>,
    val names: Map<UUID, ProductName>,
    val categories: List<MenuCategory>,
)

data class ProductQuantity(
    val hour: Int,
    val quantity: BigDecimal,
)

data class ProductSales(
    val venueId: UUID,
    val productId: UUID,
    val name: String,
    val categoryId: UUID?,
    val sold: BigDecimal,
    val grossMinor: Long,
    /** Hours with a quantity, in business-day order. */
    val hourly: List<ProductQuantity>,
    /** The hour with the most sold (a positive quantity), the first in business-day order on ties; null without one. */
    val peakHour: Int?,
)

data class ProductsFigures(
    /** The day's local hours with product sales, in business-day order; empty on a day without any. */
    val hours: List<Int>,
    val categories: List<MenuCategory>,
    val products: List<ProductSales>,
)

/**
 * Screen 02a for one business day (`docs/domain/METRICS.md`, products and hours). Pure.
 *
 * - Products are listed per venue (POS product ids are per venue; no merging by name), largest gross
 *   first, then by name, then by product id.
 * - Sold = sum of quantities, gross = sum of line totals (quantity × unit price − discount), deleted bills
 *   never counted (the rollups leave them out); hours are bill opening hours (Storyous has no item time).
 * - A product whose sold and gross both net to 0 is left out (nothing sold, nothing earned); the peak hour
 *   is the hour with the most sold among hours with a positive quantity, null without one.
 * - Categories = those of the listed products and their ancestors, by name.
 */
object ProductsCalculator {
    /** [dayHours]: the local hours of the business day in order (`BusinessDayCalendar.localHours`). */
    fun compute(
        venues: List<VenueProducts>,
        dayHours: List<Int>,
    ): ProductsFigures {
        val position = { hour: Int -> dayHours.indexOf(hour).let { if (it < 0) Int.MAX_VALUE else it } }
        val products =
            venues
                .flatMap { venue ->
                    venue.hours.groupBy { it.productId }.map { (productId, rows) ->
                        val name = venue.names[productId]
                        val hourly =
                            rows
                                .groupBy { it.hour }
                                .map { (hour, inHour) ->
                                    ProductQuantity(hour, inHour.fold(BigDecimal.ZERO) { sum, row -> sum + row.quantity })
                                }.filter { it.quantity.signum() != 0 }
                                .sortedBy { position(it.hour) }
                        ProductSales(
                            venueId = venue.venueId,
                            productId = productId,
                            name = requireNotNull(name) { "a sold product has a stored name (pos.product rows are kept)" }.name,
                            categoryId = name.categoryId,
                            sold = rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.quantity },
                            grossMinor = rows.sumOf { it.grossMinor },
                            hourly = hourly,
                            peakHour =
                                hourly
                                    .filter { it.quantity.signum() > 0 }
                                    .maxWithOrNull(compareBy<ProductQuantity> { it.quantity }.thenByDescending { position(it.hour) })
                                    ?.hour,
                        )
                    }
                }.filterNot { it.sold.signum() == 0 && it.grossMinor == 0L }
                .sortedWith(compareByDescending<ProductSales> { it.grossMinor }.thenBy { it.name }.thenBy { it.productId })
        val hours = products.flatMap { product -> product.hourly.map { it.hour } }.distinct().sortedBy(position)
        return ProductsFigures(hours, categories(venues, products), products)
    }

    private fun categories(
        venues: List<VenueProducts>,
        products: List<ProductSales>,
    ): List<MenuCategory> {
        val byId = venues.flatMap { it.categories }.associateBy { it.categoryId }
        val used = mutableSetOf<UUID>()
        for (start in products.mapNotNull { it.categoryId }) {
            var id: UUID? = start
            while (id != null && used.add(id)) id = byId[id]?.parentId
        }
        return used.mapNotNull { byId[it] }.sortedWith(compareBy<MenuCategory> { it.name }.thenBy { it.categoryId })
    }
}
