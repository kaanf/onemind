package com.onemind.sales.presentation.products

import kotlinx.datetime.LocalDate

/** Sample figures for previews only (never in production code paths, rule 2). */
internal object ProductsPreviewData {
    private fun row(
        key: String,
        name: String,
        category: String,
        sold: String,
        gross: String,
        peak: Int,
        hourly: Map<Int, String>,
    ) = ProductRow(key, name, category, category.lowercase(), null, sold, gross, peak, hourly)

    val ready =
        ProductsUi(
            businessDay = LocalDate(2026, 11, 5),
            hasData = true,
            pendingVenueCount = 0,
            hours = listOf(16, 17, 18, 19, 20, 21, 22, 23, 0, 1),
            chips = listOf(CategoryChip("beer", "Beer"), CategoryChip("cocktails", "Cocktails")),
            products =
                listOf(
                    row(
                        "a/1",
                        "Pilsner Urquell 0.5",
                        "Beer",
                        "142",
                        "9 940 Kč",
                        22,
                        mapOf(
                            16 to "4",
                            18 to "12",
                            20 to "21",
                            22 to "34",
                            23 to "28",
                            0 to "9",
                        ),
                    ),
                    row("a/2", "Aperol Spritz", "Cocktails", "38", "5 320 Kč", 21, mapOf(17 to "6", 19 to "8", 21 to "12", 22 to "7")),
                ),
            hasDataGaps = false,
        )
}
