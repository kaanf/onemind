package com.onemind.composeapp.stock

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money

/**
 * What the Stock tab landing shows (canvas `StockPlan`): stock value on hand, what needs attention and the
 * locations. Until `feature:stock` and its API exist (phase 6) the only source is [DemoStock], shown in dev
 * builds with a *Demo data* badge; prod builds keep the placeholder (rule 2: no invented figures in prod).
 */
data class StockOverview(
    val onHand: Money,
    val attention: List<StockAttention>,
    val locations: List<StockLocation>,
)

sealed interface StockAttention {
    /** A scanned delivery waiting for the owner's check (S3 Scan invoice). */
    data class DeliveryToApprove(
        val supplier: String,
        val scannedBy: String,
        val lineCount: Int,
    ) : StockAttention

    /** Items below par at [location] that another location has (S2 Pull list). */
    data class PullList(
        val itemCount: Int,
        val location: String,
        val from: String,
    ) : StockAttention

    /** An item running out in the whole venue. */
    data class Reorder(
        val item: String,
        val left: Int,
        val reorderAt: Int,
    ) : StockAttention
}

data class StockLocation(
    val name: String,
    val kind: String,
    val itemCount: Int,
    val belowParCount: Int,
    val value: Money,
    /** Deliveries land here. */
    val isReceiving: Boolean,
)

/** Fixed demo figures for the Stock tab (dev builds only). Replaced by the stock API in phase 6. */
object DemoStock {
    private fun czk(major: Long) = Money(major * 100, Currency.CZK)

    val overview =
        StockOverview(
            onHand = czk(186_400),
            attention =
                listOf(
                    StockAttention.DeliveryToApprove(supplier = "Nápoje Novák", scannedBy = "Ali", lineCount = 9),
                    StockAttention.PullList(itemCount = 5, location = "Main bar", from = "Back storage"),
                    StockAttention.Reorder(item = "Beefeater Gin 1L", left = 2, reorderAt = 4),
                ),
            locations =
                listOf(
                    StockLocation("Main bar", "Bar", itemCount = 64, belowParCount = 5, value = czk(41_200), isReceiving = false),
                    StockLocation("Back storage", "Storage", itemCount = 112, belowParCount = 0, value = czk(128_900), isReceiving = true),
                    StockLocation("Van", "Vehicle", itemCount = 18, belowParCount = 0, value = czk(16_300), isReceiving = false),
                ),
        )
}
