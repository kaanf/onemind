package com.onemind.core.domain.venue

/**
 * Which venues the dashboards show: one venue, or the *All venues* aggregate. Global state shared by
 * every tab (`docs/product/NAVIGATION.md`).
 */
sealed interface VenueScope {
    data class Venue(
        val venueId: String,
    ) : VenueScope

    data object AllVenues : VenueScope
}
