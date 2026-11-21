package com.onemind.archfixture.reporting.domain

import java.math.BigDecimal

// Rule 5 violations: a money field, a money class, a money method, a BigDecimal built from a double.
data class ReceiptLine(
    val totalPrice: Double,
)

class PriceCalculator {
    fun net(gross: Long): Double = gross / 1.21
}

class Converter {
    fun toAmount(value: Float): Long = value.toLong()
}

class Rounding {
    fun round(minor: Long): BigDecimal = BigDecimal.valueOf(minor.toDouble())
}

// Rule 5 violations a whole-word, singular-only list would miss.
class ShiftSummary(
    val tips: Double,
)

class Dashboard(
    val averageBill: Double,
)

class DayFigures(
    val netSales: Double,
)

class Charger {
    fun charge(amount: Double): Long = amount.toLong()
}

class Ledger(
    val id: String,
) {
    constructor(id: String, subtotal: Double) : this(id + subtotal)
}

class InlineRounding {
    fun round(value: Double): BigDecimal = value.toBigDecimal()
}

// Allowed: ratios and counts, even in a money class with a value-class id (Kotlin mangles its copy
// to copy-<hash>), and words that only contain a money word.
@JvmInline
value class PaymentId(
    val value: String,
)

data class PaymentSplit(
    val id: PaymentId,
    val amountMinor: Long,
    val tipShare: Double,
    val discountRate: Double,
    val totalCount: Double,
    val tipRates: List<Double>,
    val priceIndex: Double,
    val billNumber: Double,
)

data class Occupancy(
    val multiple: Double,
    val costume: Float,
    val tooltipOffset: Double,
) {
    fun scale(factor: Double): Double = multiple * factor
}
