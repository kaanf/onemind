package com.onemind.core.data.venue

import com.onemind.core.data.cache.CachedScreen
import com.onemind.core.data.cache.ScreenCache
import com.onemind.core.data.network.TestApi
import com.onemind.core.data.network.respondJson
import com.onemind.core.data.network.session
import com.onemind.core.data.profile.ProfileRepositoryImpl
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueScope
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** An in-memory [ScreenCache] for loader tests. */
private class MemoryScreenCache : ScreenCache {
    val entries = mutableMapOf<String, CachedScreen>()
    override var generation = 0L

    override suspend fun read(key: String) = entries[key]

    override suspend fun write(
        key: String,
        body: String,
        etag: String?,
        generation: Long,
    ) {
        if (generation != this.generation) return
        entries[key] = CachedScreen(body, etag, Instant.parse("2026-11-22T20:40:00Z"))
    }

    override suspend fun clear() {
        generation++
        entries.clear()
    }
}

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
            val cache = MemoryScreenCache()
            SignedInDataLoader(sessions, venues, profile, cache, backgroundScope + Dispatchers.Default).start()

            sessions.state.value = SessionState.SignedIn
            eventually { venues.venues.value is VenueList.Loaded && profile.profile.value != null }
            assertEquals(VenueScope.Venue("v1"), scope.selected.value)
            assertEquals("KF", profile.profile.value?.initials)

            cache.write("dashboard/today?venueIds=v1", "{}", null, cache.generation)
            sessions.state.value = SessionState.SignedOut
            eventually { venues.venues.value == VenueList.Loading && profile.profile.value == null }
            assertNull(scope.selected.value)
            // The read cache is account data too.
            eventually { cache.entries.isEmpty() }
        }

    @Test
    fun aSlowProfileOfTheLastAccountNeverShowsAfterSignOut() =
        runTest {
            val accountA = CompletableDeferred<Unit>()
            var meCalls = 0
            val api =
                TestApi(backgroundScope, session("a1")) { request ->
                    if (request.url.encodedPath.endsWith("/me")) {
                        if (meCalls++ == 0) {
                            // Account A's /me answers only after A signed out and B signed in.
                            accountA.await()
                            respondJson("""{"id":"uA","email":"a@b.cz","displayName":"Anna Adams","memberships":[]}""")
                        } else {
                            respondError(HttpStatusCode.ServiceUnavailable)
                        }
                    } else {
                        respondJson("""{"venues":[]}""")
                    }
                }.start()
            val sessions = FakeSessions()
            val venues = VenueRepositoryImpl(api.client, InMemoryVenueScopeRepository())
            val profile = ProfileRepositoryImpl(api.client)
            val cache = MemoryScreenCache()
            SignedInDataLoader(sessions, venues, profile, cache, backgroundScope + Dispatchers.Default).start()

            sessions.state.value = SessionState.SignedIn
            eventually { meCalls == 1 }
            sessions.state.value = SessionState.SignedOut
            // Wait for the loader to have handled the sign-out (it empties the cache), not just for a value
            // that may still be the initial one: the session is a StateFlow, so a sign-in set before the
            // collector saw SignedOut would be conflated away and A's sign-in would never be cancelled.
            eventually { cache.generation == 1L && venues.venues.value == VenueList.Loading }
            sessions.state.value = SessionState.SignedIn
            eventually { meCalls == 2 && venues.venues.value is VenueList.Loaded }

            accountA.complete(Unit)
            withContext(Dispatchers.Default) { kotlinx.coroutines.delay(200) }

            // B's /me failed: no profile, and certainly not A's.
            assertNull(profile.profile.value)
        }
}
