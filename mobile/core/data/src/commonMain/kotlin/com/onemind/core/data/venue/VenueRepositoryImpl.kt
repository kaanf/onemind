package com.onemind.core.data.venue

import com.onemind.core.api.model.MyVenues
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.domain.venue.defaultVenueScope
import com.onemind.core.domain.venue.isValidIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `GET /venues`, kept in memory for the session; [clear] on sign-out. Each load also keeps the venue
 * scope valid: it picks the default (`defaultVenueScope`) when none is selected or the selected venue is
 * gone.
 */
class VenueRepositoryImpl(
    private val client: HttpClient,
    private val venueScope: VenueScopeRepository,
) : VenueRepository {
    private val state = MutableStateFlow<VenueList>(VenueList.Loading)
    override val venues: StateFlow<VenueList> = state.asStateFlow()

    override suspend fun refresh(): EmptyResult<DataError> =
        when (val result = safeCall<MyVenues> { client.get("venues") }) {
            is Result.Success -> {
                val venues = result.data.toDomainOrNull()
                if (venues == null) {
                    fail(DataError.Network.SERIALIZATION)
                } else {
                    state.value = VenueList.Loaded(venues)
                    keepScopeValid(venues)
                    Result.Success(Unit)
                }
            }

            is Result.Failure -> {
                fail(result.error)
            }
        }

    /** A failed reload keeps a list already loaded: the screens go on with it. */
    private fun fail(error: DataError): EmptyResult<DataError> {
        if (state.value !is VenueList.Loaded) state.value = VenueList.Failed(error)
        return Result.Failure(error)
    }

    private fun keepScopeValid(venues: List<Venue>) {
        val selected = venueScope.selected.value
        if (selected != null && selected.isValidIn(venues)) return
        defaultVenueScope(venues)?.let(venueScope::select) ?: venueScope.clear()
    }

    /** Sign-out: the list and the scope belong to the account. */
    fun clear() {
        state.value = VenueList.Loading
        venueScope.clear()
    }
}

/** `null` when a currency code breaks the contract (never guessed). */
internal fun MyVenues.toDomainOrNull(): List<Venue>? =
    try {
        venues.map { Venue(id = it.id, name = it.name, currency = Currency(it.currency), followed = it.followed) }
    } catch (_: IllegalArgumentException) {
        null
    }
