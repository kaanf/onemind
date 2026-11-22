package com.onemind.today.domain

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result

/** Screen 01 (`GET /dashboard/today`). */
interface TodayRepository {
    /**
     * Today's figures for [venueIds] (one venue or the *All venues* set). Polling sends the last `ETag`
     * of the same venue set as `If-None-Match` (ADR 0005), so an unchanged answer (`304`) returns the
     * previous summary without a body.
     */
    suspend fun getToday(venueIds: List<String>): Result<TodaySummary, DataError>
}

/** Problem codes of `GET /dashboard/today` the screen handles (`docs/api/paths/dashboard/today.yaml`). */
object TodayErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
