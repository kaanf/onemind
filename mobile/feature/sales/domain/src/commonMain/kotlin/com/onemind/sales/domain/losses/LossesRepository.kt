package com.onemind.sales.domain.losses

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result

/** Screen 02c (`GET /dashboard/losses`). */
interface LossesRepository {
    /**
     * The possible loss of [period] (containing the venues' current business day) for [venueIds] (one
     * venue or the *All venues* set). A repeated request sends the last `ETag` of the same venues and
     * period as `If-None-Match` (ADR 0005), so an unchanged answer (`304`) returns the previous summary.
     */
    suspend fun getLosses(
        venueIds: List<String>,
        period: LossesPeriod,
    ): Result<LossesSummary, DataError>
}

/** Problem codes of `GET /dashboard/losses` the screen handles (`docs/api/paths/dashboard/losses.yaml`). */
object LossesErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
