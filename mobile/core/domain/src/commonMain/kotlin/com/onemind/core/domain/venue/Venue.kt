package com.onemind.core.domain.venue

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.util.DataError

/** A venue the signed-in user may open (`GET /venues`). Demo venues are named as demo by the server. */
data class Venue(
    val id: String,
    val name: String,
    val currency: Currency,
    /** Shown in Today and Sales by default (A5): *All venues* means the followed ones. */
    val followed: Boolean,
)

/** The venue list as the app knows it: not loaded yet, loaded, or the last load failed. */
sealed interface VenueList {
    data object Loading : VenueList

    data class Loaded(
        val venues: List<Venue>,
    ) : VenueList

    data class Failed(
        val error: DataError,
    ) : VenueList
}

/**
 * The scope chosen after sign-in when none is selected yet: the only venue, or *All venues* when there
 * are several; `null` without venues.
 */
fun defaultVenueScope(venues: List<Venue>): VenueScope? =
    when (venues.size) {
        0 -> null
        1 -> VenueScope.Venue(venues.single().id)
        else -> VenueScope.All
    }

/**
 * The venue ids behind a scope (`venueIds` of the dashboard calls). *All venues* is the followed
 * venues; when none is followed it is every venue the user may open (`docs/product/SCREENS.md` 01).
 * A single venue that isn't in [venues] (no longer visible) gives none.
 */
fun VenueScope.venueIds(venues: List<Venue>): List<String> =
    when (this) {
        is VenueScope.Venue -> listOfNotNull(venues.firstOrNull { it.id == venueId }?.id)
        VenueScope.All -> venues.filter { it.followed }.ifEmpty { venues }.map { it.id }
    }

/** Whether the scope still points at something the user may open. */
fun VenueScope.isValidIn(venues: List<Venue>): Boolean = venueIds(venues).isNotEmpty()
