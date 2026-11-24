package com.onemind.sales.presentation

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal const val VENUE_A = "0193a1b2-0000-7000-8000-00000000000a"
internal const val VENUE_B = "0193a1b2-0000-7000-8000-00000000000b"

internal val barA = Venue(VENUE_A, "Main bar", Currency.CZK, followed = true)
internal val barB = Venue(VENUE_B, "Garden", Currency.CZK, followed = false)
internal val bothVenues = VenueList.Loaded(listOf(barA, barB))

/** Formatted money as `formatMoney` writes it, `·` = U+202F grouping, `_` = U+00A0. */
internal fun shown(pattern: String) = pattern.replace('·', ' ').replace('_', ' ')

internal class FakeVenueScope(
    initial: VenueScope?,
) : VenueScopeRepository {
    private val state = MutableStateFlow(initial)
    override val selected: StateFlow<VenueScope?> = state

    override fun select(scope: VenueScope) {
        state.value = scope
    }

    override fun clear() {
        state.value = null
    }
}

internal class FakeVenues(
    initial: VenueList,
) : VenueRepository {
    val state = MutableStateFlow(initial)
    override val venues: StateFlow<VenueList> = state
    var refreshes = 0

    override suspend fun refresh(): EmptyResult<DataError> {
        refreshes++
        return Result.Success(Unit)
    }
}
