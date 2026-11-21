// Fixture (not compiled): plural and backend-list money words as Double in feature domain.
package com.onemind.sales.domain

data class SalesSummary(
    val prices: List<Double>,
    val amounts: List<Double>,
    val subtotal: Double,
    val salesByHour: List<Double>,
    // Not money: plural ratio/count suffixes and lookalike words.
    val tipRates: List<Double>,
    val guestCounts: List<Double>,
    val wholesaler: Double,
)
