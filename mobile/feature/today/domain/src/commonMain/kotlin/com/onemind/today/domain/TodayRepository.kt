package com.onemind.today.domain

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import kotlin.time.Instant

/** Screen 01 (`GET /dashboard/today`). */
interface TodayRepository {
    /**
     * Today's figures for [venueIds] (one venue or the *All venues* set). Polling sends the last `ETag`
     * of the same venue set as `If-None-Match` (ADR 0005), so an unchanged answer (`304`) returns the
     * previous summary without a body.
     */
    suspend fun getToday(venueIds: List<String>): Result<TodaySummary, DataError>

    /**
     * The last answer saved on the device for [venueIds] (ADR 0006), with when it was received; `null`
     * when there is none or it can't be read. Shown only with its age.
     */
    suspend fun cachedToday(venueIds: List<String>): CachedToday?
}

/** A cached Today answer and when the app received it. */
data class CachedToday(
    val summary: TodaySummary,
    val savedAt: Instant,
)

/** Problem codes of `GET /dashboard/today` the screen handles (`docs/api/paths/dashboard/today.yaml`). */
object TodayErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
