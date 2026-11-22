package com.onemind.core.data.venue

import com.onemind.core.data.network.TestApi
import com.onemind.core.data.network.respondJson
import com.onemind.core.data.network.session
import com.onemind.core.data.profile.ProfileRepositoryImpl
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignedInDataLoaderTest {
    private class FakeSessions : SessionRepository {
        override val state = MutableStateFlow<SessionState>(SessionState.Unknown)

        override suspend fun signOut() {
            state.value = SessionState.SignedOut
        }
    }

    /** Polls on real time: the mock engine answers on its own thread. */
    private suspend fun eventually(check: () -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (!check()) kotlinx.coroutines.delay(10) }
        }

    @Test
    fun signingInLoadsVenuesAndProfileAndSigningOutForgetsThem() =
        runTest {
            val api =
                TestApi(backgroundScope, session("a1")) { request ->
                    if (request.url.encodedPath.endsWith("/me")) {
                        respondJson("""{"id":"u1","email":"o@b.cz","displayName":"Kaan Fırat","memberships":[]}""")
                    } else {
                        respondJson(
                            """{"venues":[{"id":"v1","name":"Demo · Bar","timeZone":"Europe/Prague","currency":"CZK",
                               "businessDayStart":"05:00:00","followed":true,"role":"OWNER"}]}""",
                        )
                    }
                }.start()
            val sessions = FakeSessions()
            val scope = InMemoryVenueScopeRepository()
            val venues = VenueRepositoryImpl(api.client, scope)
            val profile = ProfileRepositoryImpl(api.client)
            SignedInDataLoader(sessions, venues, profile, backgroundScope + Dispatchers.Default).start()

            sessions.state.value = SessionState.SignedIn
            eventually { venues.venues.value is VenueList.Loaded && profile.profile.value != null }
            assertEquals(VenueScope.Venue("v1"), scope.selected.value)
            assertEquals("KF", profile.profile.value?.initials)

            sessions.state.value = SessionState.SignedOut
            eventually { venues.venues.value == VenueList.Loading && profile.profile.value == null }
            assertNull(scope.selected.value)
        }
}
