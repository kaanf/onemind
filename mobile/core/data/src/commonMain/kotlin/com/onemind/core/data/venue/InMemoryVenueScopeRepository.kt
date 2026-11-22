package com.onemind.core.data.venue

import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the selection for the app session only; persisting it in DataStore comes with the venue switcher. */
class InMemoryVenueScopeRepository : VenueScopeRepository {
    private val state = MutableStateFlow<VenueScope?>(null)

    override val selected: StateFlow<VenueScope?> = state.asStateFlow()

    override fun select(scope: VenueScope) {
        state.value = scope
    }

    override fun clear() {
        state.value = null
    }
}
