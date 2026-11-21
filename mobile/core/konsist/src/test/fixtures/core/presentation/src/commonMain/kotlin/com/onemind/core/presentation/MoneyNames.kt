// Fixture (not compiled): money-named Float/Double in core:presentation, next to drawing values that
// must not be flagged.
package com.onemind.core.presentation

fun formatMoney(amount: Double): String = amount.toString()

val possibleLoss: Double = 0.0

val averageBill: Float = 0f

// Not money: sizes, ratios, percentages, counts and lookalike words.
val tooltipWidth: Float = 12f
val multiplier: Double = 1.5
val totalHeight: Float = 120f
val cardRate: Double = 0.69
val discountPct: Double = 12.4
val guestCount: Double = 0.0
val network: Double = 0.0

// Drawing scales money to a bar height; allowed in presentation.
fun barHeight(
    revenueMinor: Long,
    maxRevenueMinor: Long,
): Float = revenueMinor.toFloat() / maxRevenueMinor
