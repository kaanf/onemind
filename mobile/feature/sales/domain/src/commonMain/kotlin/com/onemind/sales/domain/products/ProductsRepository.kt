package com.onemind.sales.domain.products

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result

/** Screen 02a (`GET /dashboard/products`). */
interface ProductsRepository {
    /**
     * Products sold on the venues' current business day for [venueIds], with their hourly quantities by
     * bill opening hour. A repeated request sends the last `ETag` of the same venues as `If-None-Match`
     * (ADR 0005), so an unchanged answer (`304`) returns the previous summary.
     */
    suspend fun getProducts(venueIds: List<String>): Result<ProductsSummary, DataError>
}

/** Problem codes of `GET /dashboard/products` the screen handles (`docs/api/paths/dashboard/products.yaml`). */
object ProductsErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
