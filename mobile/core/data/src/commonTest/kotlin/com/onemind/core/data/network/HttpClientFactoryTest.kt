package com.onemind.core.data.network

import com.onemind.core.domain.session.AppUpdateRequired
import com.onemind.core.domain.session.SessionState
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class HttpClientFactoryTest {
    @Test
    fun everyRequestCarriesVersionPlatformAndAFreshRequestId() =
        runTest {
            val api = TestApi(backgroundScope) { respondJson("{}") }.start()

            api.client.get("venues")
            api.client.get("venues")

            val (first, second) = api.engine.requestHistory
            assertEquals("http://api.test/api/v1/venues", first.url.toString())
            assertEquals("1.4.2", first.headers[ApiHeaders.APP_VERSION])
            assertTrue(first.headers[ApiHeaders.PLATFORM] in setOf("IOS", "ANDROID"))
            assertNotEquals(first.headers[ApiHeaders.REQUEST_ID], second.headers[ApiHeaders.REQUEST_ID])
        }

    @Test
    fun authenticatedCallsSendTheAccessTokenAndAuthCallsNever() =
        runTest {
            val api = TestApi(backgroundScope, initialSession = session("a1")) { respondJson("{}") }.start()

            api.client.get("venues")
            api.client.post("auth/email/start")

            val (venues, auth) = api.engine.requestHistory
            assertEquals("Bearer a1", venues.headers[HttpHeaders.Authorization])
            assertNull(auth.headers[HttpHeaders.Authorization])
        }

    @Test
    fun a401RefreshesOnceStoresTheRotatedTokensAndRetries() =
        runTest {
            val api =
                TestApi(backgroundScope, initialSession = session("old")) { request ->
                    when {
                        request.url.encodedPath.endsWith("auth/refresh") -> respondJson(tokensJson("new"))
                        request.headers[HttpHeaders.Authorization] == "Bearer new" -> respondJson("{}")
                        else -> respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                    }
                }.start()

            val response = api.client.get("venues")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("new", api.sessions.current()?.accessToken)
            assertEquals("refresh-new", api.sessions.current()?.refreshToken)
            val refresh = api.engine.requestHistory.single { it.url.encodedPath.endsWith("auth/refresh") }
            assertNull(refresh.headers[HttpHeaders.Authorization])
            val retried = api.engine.requestHistory.last()
            assertNotEquals(
                api.engine.requestHistory
                    .first()
                    .headers[ApiHeaders.REQUEST_ID],
                retried.headers[ApiHeaders.REQUEST_ID],
            )
        }

    @Test
    fun parallel401sShareOneRefresh() =
        runTest {
            val parallel = 5
            val oldTokenHits = AtomicInt(0)
            val refreshCalls = AtomicInt(0)
            val allRejected = CompletableDeferred<Unit>()
            val api =
                TestApi(backgroundScope, initialSession = session("old")) { request ->
                    when {
                        request.url.encodedPath.endsWith("auth/refresh") -> {
                            refreshCalls.incrementAndFetch()
                            withTimeout(5_000) { allRejected.await() }
                            respondJson(tokensJson("new"))
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer new" -> {
                            respondJson("{}")
                        }

                        else -> {
                            if (oldTokenHits.incrementAndFetch() == parallel) allRejected.complete(Unit)
                            respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                        }
                    }
                }.start()

            val statuses = List(parallel) { async { api.client.get("venues").status } }.awaitAll()

            assertTrue(statuses.all { it == HttpStatusCode.OK }, statuses.toString())
            assertEquals(1, refreshCalls.load())
            assertEquals(parallel, oldTokenHits.load())
        }

    @Test
    fun aCallerCancelledMidRefreshStillStoresTheRotatedTokens() =
        runTest {
            val refreshReached = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()
            val api =
                TestApi(backgroundScope, initialSession = session("old")) { request ->
                    when {
                        request.url.encodedPath.endsWith("auth/refresh") -> {
                            refreshReached.complete(Unit)
                            withTimeout(5_000) { releaseRefresh.await() }
                            respondJson(tokensJson("new"))
                        }

                        request.headers[HttpHeaders.Authorization] == "Bearer new" -> {
                            respondJson("{}")
                        }

                        else -> {
                            respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                        }
                    }
                }.start()

            val caller = async { api.client.get("venues") }
            awaitReal { refreshReached.await() }
            caller.cancel()
            releaseRefresh.complete(Unit)

            awaitReal {
                while (api.sessions.current()?.accessToken != "new") kotlinx.coroutines.delay(10)
            }
            assertEquals("refresh-new", api.store.value?.let { it.substringAfter("\"refreshToken\":\"").substringBefore('"') })
            assertEquals(1, api.engine.requestHistory.count { it.url.encodedPath.endsWith("auth/refresh") })
        }

    @Test
    fun aRejectedRefreshSignsOutAndReturnsThe401() =
        runTest {
            val api =
                TestApi(backgroundScope, initialSession = session("old")) { request ->
                    if (request.url.encodedPath.endsWith("auth/refresh")) {
                        respondJson(problemJson(401, "REFRESH_TOKEN_INVALID"), HttpStatusCode.Unauthorized)
                    } else {
                        respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                    }
                }.start()

            val response = api.client.get("venues")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertNull(api.sessions.current())
            assertNull(api.store.value)
            assertEquals(SessionState.SignedOut, api.sessions.state.value)
            assertEquals(1, api.engine.requestHistory.count { it.url.encodedPath.endsWith("auth/refresh") })
        }

    @Test
    fun aFailedRefreshForOtherReasonsKeepsTheSession() =
        runTest {
            val api =
                TestApi(backgroundScope, initialSession = session("old")) { request ->
                    if (request.url.encodedPath.endsWith("auth/refresh")) {
                        respondJson(problemJson(503, "SERVICE_UNAVAILABLE"), HttpStatusCode.ServiceUnavailable)
                    } else {
                        respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                    }
                }.start()

            api.client.get("venues")

            assertEquals("old", api.sessions.current()?.accessToken)
        }

    @Test
    fun a401OnAnAuthCallNeverRefreshes() =
        runTest {
            val api =
                TestApi(backgroundScope, initialSession = session("old")) {
                    respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                }.start()

            api.client.post("auth/apple")

            assertEquals(1, api.engine.requestHistory.size)
            assertEquals("old", api.sessions.current()?.accessToken)
        }

    @Test
    fun withoutASessionA401IsReturnedAsIs() =
        runTest {
            val api = TestApi(backgroundScope) { respondJson(problemJson(401, "UNAUTHORIZED"), HttpStatusCode.Unauthorized) }.start()

            assertEquals(HttpStatusCode.Unauthorized, api.client.get("venues").status)
            assertEquals(1, api.engine.requestHistory.size)
        }

    @Test
    fun a426ReportsTheMinimumVersionAndKeepsTheBodyReadable() =
        runTest {
            val api =
                TestApi(backgroundScope) {
                    respondJson(
                        problemJson(426, "APP_UPGRADE_REQUIRED", """{"platform":"IOS","minimumVersion":"1.5.0"}"""),
                        HttpStatusCode.UpgradeRequired,
                    )
                }.start()

            val result = safeCall<Unit> { api.client.post("auth/email/start") }

            assertEquals(AppUpdateRequired(minimumVersion = "1.5.0"), api.appUpdates.required.value)
            val error = (result as com.onemind.core.domain.util.Result.Failure).error
            assertEquals("APP_UPGRADE_REQUIRED", (error as com.onemind.core.domain.util.DataError.Api).code)
        }

    @Test
    fun a426WithAnotherCodeIsNotAnUpdate() =
        runTest {
            val api = TestApi(backgroundScope) { respondJson(problemJson(426, "SOMETHING_ELSE"), HttpStatusCode.UpgradeRequired) }.start()

            api.client.get("venues")

            assertNull(api.appUpdates.required.value)
        }
}
