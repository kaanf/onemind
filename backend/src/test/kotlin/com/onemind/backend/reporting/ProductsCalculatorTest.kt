package com.onemind.backend.reporting

import com.onemind.backend.reporting.domain.MenuCategory
import com.onemind.backend.reporting.domain.ProductHour
import com.onemind.backend.reporting.domain.ProductName
import com.onemind.backend.reporting.domain.ProductQuantity
import com.onemind.backend.reporting.domain.ProductsCalculator
import com.onemind.backend.reporting.domain.VenueProducts
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class ProductsCalculatorTest {
    private val venueA = UUID.fromString("00000000-0000-7000-8000-0000000000a1")
    private val venueB = UUID.fromString("00000000-0000-7000-8000-0000000000b1")
    private val beer = UUID.fromString("00000000-0000-7000-8000-0000000000e1")
    private val wine = UUID.fromString("00000000-0000-7000-8000-0000000000e2")
    private val cola = UUID.fromString("00000000-0000-7000-8000-0000000000e3")
    private val drinks = UUID.fromString("00000000-0000-7000-8000-0000000000c1")
    private val alcohol = UUID.fromString("00000000-0000-7000-8000-0000000000c2")
    private val unused = UUID.fromString("00000000-0000-7000-8000-0000000000c3")

    // A bar day from 05:00: 05, 06, …, 23, 0, 1, 2, 3, 4.
    private val dayHours = (5..23).toList() + (0..4).toList()

    private fun row(
        product: UUID,
        hour: Int,
        quantity: String,
        gross: Long,
    ) = ProductHour(product, hour, BigDecimal(quantity), gross)

    private val categories =
        listOf(
            MenuCategory(drinks, "Drinks", null),
            MenuCategory(alcohol, "Alcohol", drinks),
            MenuCategory(unused, "Food", null),
        )

    @Test
    fun `products sum their hours, sort by gross then name, and keep bill opening hours in business-day order`() {
        val figures =
            ProductsCalculator.compute(
                listOf(
                    VenueProducts(
                        venueA,
                        listOf(row(beer, 23, "4", 2_000), row(beer, 1, "6", 3_000), row(wine, 21, "1.5", 5_000)),
                        mapOf(beer to ProductName("Beer", alcohol), wine to ProductName("Wine", alcohol)),
                        categories,
                    ),
                ),
                dayHours,
            )

        assertEquals(listOf(beer, wine), figures.products.map { it.productId })
        val beerSales = figures.products.first()
        assertEquals(BigDecimal("10"), beerSales.sold)
        assertEquals(5_000, beerSales.grossMinor)
        // 23:00 comes before 01:00 in the bar day.
        assertEquals(listOf(ProductQuantity(23, BigDecimal("4")), ProductQuantity(1, BigDecimal("6"))), beerSales.hourly)
        assertEquals(1, beerSales.peakHour)
        assertEquals(listOf(21, 23, 1), figures.hours)
        // Alcohol and its parent Drinks; Food has no sold product.
        assertEquals(listOf("Alcohol", "Drinks"), figures.categories.map { it.name })
    }

    @Test
    fun `a peak tie goes to the first hour in business-day order, a gross tie sorts by name then id`() {
        val figures =
            ProductsCalculator.compute(
                listOf(
                    VenueProducts(
                        venueA,
                        listOf(row(beer, 0, "3", 1_000), row(beer, 22, "3", 1_000), row(cola, 20, "2", 2_000)),
                        mapOf(beer to ProductName("Beer", null), cola to ProductName("Beer", null)),
                        emptyList(),
                    ),
                ),
                dayHours,
            )

        assertEquals(22, figures.products.first { it.productId == beer }.peakHour)
        assertEquals(listOf(beer, cola), figures.products.map { it.productId })
    }

    @Test
    fun `venues keep their own product rows, never merged by name`() {
        val figures =
            ProductsCalculator.compute(
                listOf(
                    VenueProducts(venueA, listOf(row(beer, 20, "1", 500)), mapOf(beer to ProductName("Beer", null)), emptyList()),
                    VenueProducts(venueB, listOf(row(wine, 20, "1", 500)), mapOf(wine to ProductName("Beer", null)), emptyList()),
                ),
                dayHours,
            )

        assertEquals(listOf(venueA to beer, venueB to wine), figures.products.map { it.venueId to it.productId })
    }

    @Test
    fun `a day without product sales is empty, not missing`() {
        val figures = ProductsCalculator.compute(listOf(VenueProducts(venueA, emptyList(), emptyMap(), categories)), dayHours)

        assertEquals(emptyList(), figures.products)
        assertEquals(emptyList(), figures.hours)
        assertEquals(emptyList(), figures.categories)
    }
}
