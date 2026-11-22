package com.onemind.core.domain.venue

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.StateFlow

/** The signed-in user's venues (`GET /venues`), shared by every tab. */
interface VenueRepository {
    val venues: StateFlow<VenueList>

    /** Loads the list again; on failure [venues] becomes [VenueList.Failed] unless a list is already loaded. */
    suspend fun refresh(): EmptyResult<DataError>
}
