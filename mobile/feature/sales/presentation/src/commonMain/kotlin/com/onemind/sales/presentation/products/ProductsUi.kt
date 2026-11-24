package com.onemind.sales.presentation.products

import com.onemind.core.presentation.format.VenueLocale
import com.onemind.core.presentation.format.formatMoney
import com.onemind.sales.domain.products.ProductCategory
import com.onemind.sales.domain.products.ProductsSummary
import kotlinx.datetime.LocalDate

/** 02a ready to draw: the day's products formatted, plus what the chips and the chart need. */
data class ProductsUi(
    val businessDay: LocalDate,
    /** `false` while no venue has data for the day: no figure is drawn (rule 2). */
    val hasData: Boolean,
    val pendingVenueCount: Int,
    /** The day's hours with sales, in business-day order: the chart's slots. */
    val hours: List<Int>,
    /**
     * Top-level menu categories that have products today ("Beer", "Cocktails"…), by name: venues have their
     * own category ids, so *All venues* shows "Food" once, not once per venue. In order of first sale.
     */
    val chips: List<CategoryChip>,
    /** Largest gross first, as sent. */
    val products: List<ProductRow>,
    val hasDataGaps: Boolean,
)

/** A chip; [id] is the category name (the same name in several venues is one chip). */
data class CategoryChip(
    val id: String,
    val name: String,
)

data class ProductRow(
    /** Unique across venues (product ids are per venue). */
    val key: String,
    val name: String,
    /** The product's own category name, if any. */
    val categoryName: String?,
    /** Top-level category name, the chip it belongs to; `null` without a category. */
    val rootCategory: String?,
    /** Shown only when the day lists products of several venues (never merged by name). */
    val venueName: String?,
    /** Quantity sold, as sent ("12", "1.5"). */
    val sold: String,
    /** "4 320 Kč". */
    val gross: String,
    val peakHour: Int?,
    /** Quantity per hour of the day, as sent; hours without sales are missing. */
    val hourly: Map<Int, String>,
)

/** One chart bar: [heightFraction] against the product's busiest hour, 0–1; `null` draws nothing. */
data class HourBar(
    val hour: Int,
    val quantity: String?,
    val heightFraction: Float?,
    val isPeak: Boolean,
)

internal fun ProductsSummary.toUi(venueNames: Map<String, String>): ProductsUi {
    val locale = VenueLocale.forCurrency(currency)
    val byId = categories.associateBy { it.id }
    val severalVenues = products.map { it.venueId }.distinct().size > 1
    val rows =
        products.map { product ->
            ProductRow(
                key = "${product.venueId}/${product.productId}",
                name = product.name,
                categoryName = product.categoryId?.let { byId[it]?.name },
                rootCategory = product.categoryId?.let { root(it, byId)?.name },
                venueName = if (severalVenues) venueNames[product.venueId] else null,
                sold = product.sold.toString(),
                gross = formatMoney(product.gross, locale),
                peakHour = product.peakHour,
                hourly = product.hourly.associate { it.hour to it.quantity.toString() },
            )
        }
    return ProductsUi(
        businessDay = businessDay,
        hasData = hasData,
        pendingVenueCount = pendingVenueCount,
        hours = hours,
        chips = rows.mapNotNull { it.rootCategory }.distinct().map { CategoryChip(it, it) },
        products = rows,
        hasDataGaps = !dataGaps.isEmpty,
    )
}

/** The top-level ancestor of [id]; stops on a cycle or a missing parent. */
private fun root(
    id: String,
    byId: Map<String, ProductCategory>,
): ProductCategory? {
    var current = byId[id] ?: return null
    val seen = mutableSetOf(current.id)
    while (true) {
        val parent = current.parentId?.let { byId[it] } ?: return current
        if (!seen.add(parent.id)) return current
        current = parent
    }
}

/** The products under [categoryId] (`null` = all). */
internal fun ProductsUi.visibleProducts(categoryId: String?): List<ProductRow> =
    if (categoryId == null) products else products.filter { it.rootCategory == categoryId }

/** Bars for [product] over the day's hours; the height is a drawing value only, never shown as a figure. */
internal fun ProductsUi.bars(product: ProductRow): List<HourBar> {
    val heights = product.hourly.mapValues { (_, quantity) -> quantity.toFloatOrNull() ?: 0f }
    val largest = heights.values.maxOrNull()?.takeIf { it > 0f }
    return hours.map { hour ->
        val height = heights[hour]
        HourBar(
            hour = hour,
            quantity = product.hourly[hour],
            heightFraction = height?.let { if (largest == null) 0f else it / largest },
            isPeak = hour == product.peakHour,
        )
    }
}
