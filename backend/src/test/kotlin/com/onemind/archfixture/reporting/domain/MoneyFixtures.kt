package com.onemind.archfixture.reporting.domain

import java.math.BigDecimal

// Rule 5 violations: a money field, a money class, a money method, a BigDecimal built from a double.
data class BillRow(
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

// Allowed: ratios and counts, even in a money class, and words that only contain a money token.
data class PaymentSplit(
    val amountMinor: Long,
    val tipShare: Double,
    val discountRate: Double,
    val totalCount: Double,
)

data class Occupancy(
    val multiple: Double,
    val costume: Float,
)
