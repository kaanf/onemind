package com.onemind.core.data.venue

import com.onemind.core.data.network.TestApi
import com.onemind.core.data.network.awaitReal
import com.onemind.core.data.network.respondJson
import com.onemind.core.data.network.session
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueScope
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VenueRepositoryImplTest {
    private fun venuesJson(vararg venues: Pair<String, Boolean>) =
        venues.joinToString(prefix = """{"venues":[""", postfix = "]}") { (id, followed) ->
            """{"id":"$id","name":"Demo · $id","timeZone":"Europe/Prague","currency":"CZK",
                "businessDayStart":"05:00:00","followed":$followed,"role":"OWNER"}"""
        }

    @Test
    fun loadsTheVenuesAndPicksTheOnlyOne() =
        runTest {
            val scope = InMemoryVenueScopeRepository()
            val api = TestApi(backgroundScope, session("a1")) { respondJson(venuesJson("v1" to true)) }.start()
            val repository = VenueRepositoryImpl(api.client, scope)

            awaitReal { repository.refresh() }

            assertEquals(
                VenueList.Loaded(listOf(Venue("v1", "Demo · v1", Currency.CZK, followed = true))),
                repository.venues.value,
            )
            assertEquals(
                "/api/v1/venues",
                api.engine.requestHistory
                    .single()
                    .url.encodedPath,
            )
            assertEquals(VenueScope.Venue("v1"), scope.selected.value)
        }

    @Test
    fun severalVenuesDefaultToAllVenues() =
        runTest {
            val scope = InMemoryVenueScopeRepository()
            val api = TestApi(backgroundScope, session("a1")) { respondJson(venuesJson("v1" to true, "v2" to false)) }.start()

            awaitReal { VenueRepositoryImpl(api.client, scope).refresh() }

            assertEquals(VenueScope.All, scope.selected.value)
        }

    @Test
    fun aValidSelectionIsKeptAndAVanishedOneReplaced() =
        runTest {
            val scope = InMemoryVenueScopeRepository()
            val api = TestApi(backgroundScope, session("a1")) { respondJson(venuesJson("v1" to true, "v2" to true)) }.start()
            val repository = VenueRepositoryImpl(api.client, scope)

            scope.select(VenueScope.Venue("v2"))
            awaitReal { repository.refresh() }
            assertEquals(VenueScope.Venue("v2"), scope.selected.value)

            scope.select(VenueScope.Venue("gone"))
            awaitReal { repository.refresh() }
            assertEquals(VenueScope.All, scope.selected.value)
        }

    @Test
    fun noVenuesMeansNoScope() =
        runTest {
            val scope = InMemoryVenueScopeRepository().apply { select(VenueScope.Venue("old")) }
            val api = TestApi(backgroundScope, session("a1")) { respondJson("""{"venues":[]}""") }.start()

            awaitReal { VenueRepositoryImpl(api.client, scope).refresh() }

            assertNull(scope.selected.value)
        }

    @Test
    fun aFailedFirstLoadIsFailedAndAFailedReloadKeepsTheList() =
        runTest {
            var fail = true
            val scope = InMemoryVenueScopeRepository()
            val api =
                TestApi(backgroundScope, session("a1")) {
                    if (fail) respondError(HttpStatusCode.ServiceUnavailable) else respondJson(venuesJson("v1" to true))
                }.start()
            val repository = VenueRepositoryImpl(api.client, scope)

            assertIs<Result.Failure<DataError>>(awaitReal { repository.refresh() })
            assertIs<VenueList.Failed>(repository.venues.value)

            fail = false
            awaitReal { repository.refresh() }
            fail = true
            assertIs<Result.Failure<DataError>>(awaitReal { repository.refresh() })
            assertIs<VenueList.Loaded>(repository.venues.value)
        }

    @Test
    fun aBadCurrencyIsUnreadableNotGuessed() =
        runTest {
            val scope = InMemoryVenueScopeRepository()
            val api = TestApi(backgroundScope, session("a1")) { respondJson(venuesJson("v1" to true).replace("CZK", "Kč")) }.start()
            val repository = VenueRepositoryImpl(api.client, scope)

            val error = assertIs<Result.Failure<DataError>>(awaitReal { repository.refresh() }).error

            assertEquals(DataError.Network.SERIALIZATION, error)
        }

    @Test
    fun clearForgetsTheListAndTheScope() =
        runTest {
            val scope = InMemoryVenueScopeRepository()
            val api = TestApi(backgroundScope, session("a1")) { respondJson(venuesJson("v1" to true)) }.start()
            val repository = VenueRepositoryImpl(api.client, scope)
            awaitReal { repository.refresh() }

            repository.clear()

            assertEquals(VenueList.Loading, repository.venues.value)
            assertNull(scope.selected.value)
        }
}
