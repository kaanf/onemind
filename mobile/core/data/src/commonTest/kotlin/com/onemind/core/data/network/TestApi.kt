package com.onemind.core.data.network

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.data.session.FakeSecureSessionStore
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.session.StoredSession
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant

val testConfig = ApiConfig(environment = AppEnvironment.DEV, baseUrl = "http://api.test", appVersion = "1.4.2")

fun session(accessToken: String) =
    StoredSession(
        accessToken = accessToken,
        refreshToken = "refresh-$accessToken",
        accessTokenExpiresAt = Instant.parse("2026-11-22T10:15:00Z"),
        refreshTokenExpiresAt = Instant.parse("2026-12-22T10:00:00Z"),
    )

fun tokensJson(accessToken: String) =
    """
    {"accessToken":"$accessToken","accessTokenExpiresAt":"2026-11-22T10:30:00Z",
     "refreshToken":"refresh-$accessToken","refreshTokenExpiresAt":"2026-12-22T10:15:00Z"}
    """.trimIndent()

fun problemJson(
    status: Int,
    code: String,
    params: String = "{}",
) = """{"title":"$code","status":$status,"code":"$code","detail":"English detail","params":$params}"""

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** A client wired like production (factory, session manager, refresher) over a [MockEngine]. */
class TestApi(
    scope: CoroutineScope,
    private val initialSession: StoredSession? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val store = FakeSecureSessionStore()
    val sessions = SessionManager(store, scope)
    val appUpdates = InMemoryAppUpdateRepository()
    val engine = MockEngine { request -> handler(request) }
    val client: HttpClient = HttpClientFactory(testConfig, sessions, SessionRefresher(sessions), appUpdates).create(engine)

    suspend fun start(): TestApi {
        initialSession?.let { sessions.save(it) }
        return this
    }
}
