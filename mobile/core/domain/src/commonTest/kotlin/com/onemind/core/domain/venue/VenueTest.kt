package com.onemind.core.domain.venue

import com.onemind.core.domain.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VenueTest {
    private fun venue(
        id: String,
        followed: Boolean = true,
    ) = Venue(id = id, name = "Bar $id", currency = Currency.CZK, followed = followed)

    @Test
    fun theDefaultScopeIsTheOnlyVenueOrAllVenues() {
        assertNull(defaultVenueScope(emptyList()))
        assertEquals(VenueScope.Venue("a"), defaultVenueScope(listOf(venue("a"))))
        assertEquals(VenueScope.All, defaultVenueScope(listOf(venue("a"), venue("b"))))
    }

    @Test
    fun allVenuesAreTheFollowedOnes() {
        val venues = listOf(venue("a"), venue("b", followed = false), venue("c"))

        assertEquals(listOf("a", "c"), VenueScope.All.venueIds(venues))
    }

    @Test
    fun withoutFollowedVenuesAllVenuesIsEveryVenue() {
        val venues = listOf(venue("a", followed = false), venue("b", followed = false))

        assertEquals(listOf("a", "b"), VenueScope.All.venueIds(venues))
    }

    @Test
    fun aSingleVenueMustStillBeVisible() {
        val venues = listOf(venue("a"))

        assertEquals(listOf("a"), VenueScope.Venue("a").venueIds(venues))
        assertEquals(emptyList(), VenueScope.Venue("gone").venueIds(venues))
        assertTrue(VenueScope.Venue("a").isValidIn(venues))
        assertFalse(VenueScope.Venue("gone").isValidIn(venues))
        assertFalse(VenueScope.All.isValidIn(emptyList()))
    }
}
