package com.onemind.core.presentation.venue

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.venueIds

/** What a dashboard screen loads for the selected scope (same rules as Today). */
sealed interface VenueTarget {
    data object Waiting : VenueTarget

    data object NoVenue : VenueTarget

    data class VenuesFailed(
        val error: DataError,
    ) : VenueTarget

    data class Venues(
        val ids: List<String>,
    ) : VenueTarget
}

fun venueTarget(
    scope: VenueScope?,
    list: VenueList,
): VenueTarget =
    when (list) {
        VenueList.Loading -> {
            VenueTarget.Waiting
        }

        is VenueList.Failed -> {
            VenueTarget.VenuesFailed(list.error)
        }

        is VenueList.Loaded -> {
            val ids = scope?.venueIds(list.venues).orEmpty()
            when {
                list.venues.isEmpty() -> VenueTarget.NoVenue
                ids.isEmpty() -> VenueTarget.Waiting
                else -> VenueTarget.Venues(ids)
            }
        }
    }
