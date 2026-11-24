package com.onemind.sales.presentation.products

import com.onemind.sales.domain.products.ProductCategory
import com.onemind.sales.presentation.VENUE_A
import com.onemind.sales.presentation.VENUE_B
import com.onemind.sales.presentation.products.ProductsFixtures.ready
import com.onemind.sales.presentation.products.ProductsFixtures.twoVenues
import com.onemind.sales.presentation.shown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductsUiTest {
    private val names = mapOf(VENUE_A to "Main bar", VENUE_B to "Garden")

    @Test
    fun chipsAreTopLevelCategoriesWithProducts() {
        val ui = ready.toUi(names)

        assertEquals(listOf(CategoryChip("Beer", "Beer"), CategoryChip("Cocktails", "Cocktails")), ui.chips)
        assertEquals("Beer", ui.products[0].rootCategory)
        assertEquals("Lager", ui.products[0].categoryName)
        assertNull(ui.products[2].rootCategory)
    }

    @Test
    fun rowsAreFormattedAsSent() {
        val row = ready.toUi(names).products[0]

        assertEquals("$VENUE_A/p1", row.key)
        assertEquals("40", row.sold)
        assertEquals(shown("2·800_Kč"), row.gross)
        assertNull(row.venueName)
    }

    @Test
    fun severalVenuesNameTheVenueAndKeepProductsApart() {
        val rows = twoVenues.toUi(names).products

        assertEquals(listOf("Main bar", "Garden"), rows.map { it.venueName })
        assertEquals(2, rows.map { it.key }.distinct().size)
    }

    @Test
    fun theSameCategoryNameInTwoVenuesIsOneChip() {
        val summary =
            twoVenues.copy(
                categories = twoVenues.categories + ProductCategory("beer-b", "Beer", null) + ProductCategory("lager-b", "Lager", "beer-b"),
                products = listOf(ProductsFixtures.pilsner, ProductsFixtures.pilsner.copy(venueId = VENUE_B, categoryId = "lager-b")),
            )

        val ui = summary.toUi(names)

        assertEquals(listOf(CategoryChip("Beer", "Beer")), ui.chips)
        assertEquals(2, ui.visibleProducts("Beer").size)
    }

    @Test
    fun aChipFiltersOnTheDevice() {
        val ui = ready.toUi(names)

        assertEquals(listOf("Pilsner"), ui.visibleProducts("Beer").map { it.name })
        assertEquals(3, ui.visibleProducts(null).size)
    }

    @Test
    fun barsCoverEveryHourOfTheDayAndMarkThePeak() {
        val ui = ready.toUi(names)

        val bars = ui.bars(ui.products[0])

        assertEquals(listOf(21, 22, 23, 0), bars.map { it.hour })
        assertEquals(listOf(0.5f, 1f, null, 0.5f), bars.map { it.heightFraction })
        assertEquals(listOf("10", "20", null, "10"), bars.map { it.quantity })
        assertEquals(listOf(false, true, false, false), bars.map { it.isPeak })
    }
}
