package com.onemind.sales.domain

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import kotlin.time.Instant

/** Screen 02 (`GET /dashboard/sales`). */
interface SalesRepository {
    /**
     * The [period] containing the current business day for [venueIds] (one venue or the *All venues*
     * set). Polling sends the last `ETag` of the same venues and period as `If-None-Match` (ADR 0005),
     * so an unchanged answer (`304`) returns the previous summary without a body.
     */
    suspend fun getSales(
        venueIds: List<String>,
        period: SalesPeriod,
    ): Result<SalesSummary, DataError>

    /** The last answer saved on the device for [venueIds] and [period] (ADR 0006); `null` when none. */
    suspend fun cachedSales(
        venueIds: List<String>,
        period: SalesPeriod,
    ): CachedSales?
}

/** A cached Sales answer and when the app received it. */
data class CachedSales(
    val summary: SalesSummary,
    val savedAt: Instant,
)

/** Problem codes of `GET /dashboard/sales` the screen handles (`docs/api/paths/dashboard/sales.yaml`). */
object SalesErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
