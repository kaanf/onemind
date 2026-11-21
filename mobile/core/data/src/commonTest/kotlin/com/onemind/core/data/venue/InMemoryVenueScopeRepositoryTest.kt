package com.onemind.core.data.venue

import com.onemind.core.domain.venue.VenueScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryVenueScopeRepositoryTest {
    @Test
    fun startsWithoutSelection() {
        assertNull(InMemoryVenueScopeRepository().selected.value)
    }

    @Test
    fun selectReplacesTheScope() {
        val repository = InMemoryVenueScopeRepository()

        repository.select(VenueScope.Venue("venue-1"))
        assertEquals(VenueScope.Venue("venue-1"), repository.selected.value)

        repository.select(VenueScope.All)
        assertEquals(VenueScope.All, repository.selected.value)
    }
}
