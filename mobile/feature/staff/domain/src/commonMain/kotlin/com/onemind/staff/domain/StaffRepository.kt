package com.onemind.staff.domain

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result

/** Screen 03 (`GET /dashboard/staff`). */
interface StaffRepository {
    /**
     * Staff figures of the venues' current business day for [venueIds]. A repeated request sends the last
     * `ETag` of the same venues as `If-None-Match` (ADR 0005), so an unchanged answer (`304`) returns the
     * previous summary.
     */
    suspend fun getStaff(venueIds: List<String>): Result<StaffSummary, DataError>
}

/** Problem codes of `GET /dashboard/staff` the screen handles (`docs/api/paths/dashboard/staff.yaml`). */
object StaffErrorCodes {
    const val VENUE_NOT_FOUND = "VENUE_NOT_FOUND"
    const val CURRENCIES_DIFFER = "CURRENCIES_DIFFER"
    const val BUSINESS_DAYS_DIFFER = "BUSINESS_DAYS_DIFFER"
}
