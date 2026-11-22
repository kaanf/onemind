package com.onemind.auth.data

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.data.network.HttpClientFactory
import com.onemind.core.data.session.SecureSessionStore
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import com.onemind.core.domain.session.SessionState
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthRepositoryImplTest {
    private class MemoryStore(
        val failWrites: Boolean = false,
    ) : SecureSessionStore {
        var value: String? = null

        override suspend fun read() = value

        override suspend fun write(value: String) {
            check(!failWrites) { "keystore unavailable" }
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        store: SecureSessionStore = MemoryStore(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        val sessions = SessionManager(store, scope)
        val engine = MockEngine { handler(it) }
        val repository =
            AuthRepositoryImpl(
                client =
                    HttpClientFactory(
                        ApiConfig(AppEnvironment.DEV, "http://api.test", "1.0.0"),
                        sessions,
                        SessionRefresher(sessions, scope),
                        InMemoryAppUpdateRepository(),
                    ).create(engine),
                sessions = sessions,
            )
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private val tokens =
        """{"accessToken":"a1","accessTokenExpiresAt":"2026-11-22T10:15:00Z","refreshToken":"r1","refreshTokenExpiresAt":"2026-12-22T10:00:00Z"}"""

    @Test
    fun sendCodePostsTheTrimmedEmail() =
        runTest {
            val fixture = Fixture(backgroundScope) { respond("", HttpStatusCode.Accepted) }

            assertIs<Result.Success<Unit>>(fixture.repository.sendCode("  owner@yourbar.cz "))

            val request = fixture.engine.requestHistory.single()
            assertEquals("/api/v1/auth/email/start", request.url.encodedPath)
            assertEquals("""{"email":"owner@yourbar.cz"}""", request.bodyText())
        }

    @Test
    fun verifyStoresTheTokensAndSignsIn() =
        runTest {
            val fixture = Fixture(backgroundScope) { json(tokens) }

            assertIs<Result.Success<Unit>>(fixture.repository.verifyCode("owner@yourbar.cz", "482913"))

            val request = fixture.engine.requestHistory.single()
            assertEquals("/api/v1/auth/email/verify", request.url.encodedPath)
            assertEquals("""{"email":"owner@yourbar.cz","code":"482913"}""", request.bodyText())
            assertEquals("r1", fixture.sessions.current()?.refreshToken)
            assertEquals(SessionState.SignedIn, fixture.sessions.state.value)
        }

    @Test
    fun aWrongCodeReturnsTheProblemAndStaysSignedOut() =
        runTest {
            val fixture =
                Fixture(backgroundScope) {
                    json(
                        """{"title":"x","status":422,"code":"EMAIL_CODE_INVALID","params":{"attemptsLeft":2}}""",
                        HttpStatusCode.UnprocessableEntity,
                    )
                }

            val error = assertIs<Result.Failure<DataError>>(fixture.repository.verifyCode("owner@yourbar.cz", "000000")).error

            assertEquals(DataError.Api(422, "EMAIL_CODE_INVALID", params = mapOf("attemptsLeft" to "2")), error)
            assertNull(fixture.sessions.current())
        }

    @Test
    fun aStorageFailureIsAnErrorNotACrash() =
        runTest {
            val fixture = Fixture(backgroundScope, store = MemoryStore(failWrites = true)) { json(tokens) }

            val error = assertIs<Result.Failure<DataError>>(fixture.repository.verifyCode("owner@yourbar.cz", "482913")).error

            assertEquals(DataError.Network.UNKNOWN, error)
            assertNull(fixture.sessions.current())
        }
}
