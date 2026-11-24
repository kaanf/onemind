package com.onemind.sales.presentation.products

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.sales.domain.products.HourQuantity
import com.onemind.sales.domain.products.ProductCategory
import com.onemind.sales.domain.products.ProductSales
import com.onemind.sales.domain.products.ProductsDataGaps
import com.onemind.sales.domain.products.ProductsSummary
import com.onemind.sales.presentation.VENUE_A
import com.onemind.sales.presentation.VENUE_B
import kotlinx.datetime.LocalDate

internal object ProductsFixtures {
    fun czk(major: Long) = Money(major * 100, Currency.CZK)

    private fun product(
        venueId: String,
        id: String,
        name: String,
        category: String?,
        sold: String,
        gross: Long,
        peak: Int?,
        vararg hourly: Pair<Int, String>,
    ) = ProductSales(
        venueId = venueId,
        productId = id,
        name = name,
        categoryId = category,
        sold = DecimalValue.parse(sold),
        gross = czk(gross),
        hourly = hourly.map { HourQuantity(it.first, DecimalValue.parse(it.second)) },
        peakHour = peak,
    )

    val pilsner = product(VENUE_A, "p1", "Pilsner", "lager", "40", 2_800, 22, 21 to "10", 22 to "20", 0 to "10")
    val spritz = product(VENUE_A, "p2", "Aperol Spritz", "cocktails", "5", 700, 21, 21 to "5")
    val water = product(VENUE_A, "p3", "Water", null, "2", 80, null)

    val ready =
        ProductsSummary(
            hasData = true,
            currency = Currency.CZK,
            businessDay = LocalDate(2026, 11, 5),
            pendingVenueCount = 0,
            hours = listOf(21, 22, 23, 0),
            categories =
                listOf(
                    ProductCategory("beer", "Beer", null),
                    ProductCategory("lager", "Lager", "beer"),
                    ProductCategory("cocktails", "Cocktails", null),
                ),
            products = listOf(pilsner, spritz, water),
            dataGaps = ProductsDataGaps(0, 0, 0),
        )

    val twoVenues = ready.copy(products = listOf(pilsner, pilsner.copy(venueId = VENUE_B)))
}
