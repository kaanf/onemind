package com.onemind.core.data.network

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import com.onemind.core.domain.session.AppUpdateRequired
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid

/**
 * The one HTTP client for `/api/v1` (ADR 0011). Every request gets `X-App-Version`, `X-Platform`
 * and a fresh `X-Request-Id`; authenticated requests get the stored access token and, on `401`,
 * one single-flight refresh and one retry. the `auth/...` endpoints are public (`security: []`): they
 * never carry a token and never trigger a refresh. Any `426 APP_UPGRADE_REQUIRED` is reported to
 * [InMemoryAppUpdateRepository]. No logging plugin: nothing writes tokens or bodies anywhere.
 */
class HttpClientFactory(
    private val config: ApiConfig,
    private val sessions: SessionManager,
    private val refresher: SessionRefresher,
    private val appUpdates: InMemoryAppUpdateRepository,
) {
    fun create(engine: HttpClientEngine): HttpClient {
        val client =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json(ApiJson) }
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 20_000
                }
                defaultRequest {
                    url("${config.baseUrl}$API_PATH")
                    contentType(ContentType.Application.Json)
                    header(ApiHeaders.APP_VERSION, config.appVersion)
                    header(ApiHeaders.PLATFORM, platformHeaderValue)
                }
            }

        client.plugin(HttpSend).intercept { request ->
            request.newRequestId()
            val session = if (request.isPublicAuthCall()) null else sessions.current()
            session?.let { request.bearer(it.accessToken) }

            var call = execute(request)
            if (session != null && call.response.status == HttpStatusCode.Unauthorized) {
                val refreshed = refresher.refresh(client, rejectedAccessToken = session.accessToken)
                if (refreshed != null) {
                    request.newRequestId()
                    request.bearer(refreshed.accessToken)
                    call = execute(request)
                }
            }
            if (call.response.status == HttpStatusCode.UpgradeRequired) {
                val problem = call.response.readProblem()
                if (problem?.code == APP_UPGRADE_REQUIRED) {
                    appUpdates.report(AppUpdateRequired(minimumVersion = problem.params?.get("minimumVersion")?.toPlainString()))
                }
            }
            call
        }
        return client
    }

    private fun HttpRequestBuilder.isPublicAuthCall(): Boolean = url.encodedPath.startsWith("${API_PATH}auth/")

    private fun HttpRequestBuilder.bearer(accessToken: String) {
        headers[HttpHeaders.Authorization] = "Bearer $accessToken"
    }

    private fun HttpRequestBuilder.newRequestId() {
        headers[ApiHeaders.REQUEST_ID] = Uuid.random().toString()
    }

    private fun kotlinx.serialization.json.JsonElement.toPlainString(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content

    private companion object {
        const val API_PATH = "/api/v1/"
        const val APP_UPGRADE_REQUIRED = "APP_UPGRADE_REQUIRED"
    }
}
