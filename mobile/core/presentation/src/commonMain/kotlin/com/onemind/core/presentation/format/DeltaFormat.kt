package com.onemind.core.presentation.format

import com.onemind.core.domain.quantity.DecimalValue

/**
 * A percentage change as the API sends it (`"12.4"`, `"-3.0"`, one decimal already rounded by the
 * backend), formatted per METRICS.md: `+12.4%`, `−3.0%` (U+2212), `0.0%`. The digits are kept as sent,
 * never re-rounded. English form for now; the Czech form (`12,4 %`) comes with the Czech strings.
 */
fun formatDeltaPercent(delta: DecimalValue): String {
    val digits = delta.toString().removePrefix("-")
    val sign =
        when {
            delta.isZero -> ""
            delta.isNegative -> MINUS
            else -> "+"
        }
    return "$sign$digits%"
}

private const val MINUS = "−"
