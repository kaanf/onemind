package com.onemind.sales.presentation.sales

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long Sales waits between polls while visible (ADR 0005). ADR 0005 names no interval for 02; it
 * uses the Staff/Tables 60 s, as the period figures move slower than Today's.
 */
fun interface SalesPollInterval {
    fun next(): Duration

    companion object {
        val Default = SalesPollInterval { 60.seconds }
    }
}
