package com.onemind.backend.testing

import com.onemind.api.v1.client.AuthApi
import com.onemind.api.v1.client.MeApi
import com.onemind.api.v1.client.model.AuthTokens
import com.onemind.api.v1.client.model.EmailCodeStartRequest
import com.onemind.api.v1.client.model.EmailCodeVerifyRequest
import com.onemind.api.v1.client.model.Problem
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The API as an app sees it: Spring HTTP interfaces generated from `docs/api/openapi.yaml`
 * (`openApiGenerateTestClient`) over real HTTP. Responses are read strictly, so a body that drifts from
 * the spec fails the test: an unknown field fails here, a missing or null required field fails on the
 * generated model's non-null Kotlin type. A spec field marked `nullable` may be an explicit `null`.
 */
class ApiClient(
    private val port: Int,
    mapper: JsonMapper,
    private val platform: String = "ios",
    private val appVersion: String = "1.0.0",
) {
    private val strict =
        mapper
            .rebuild()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    val auth: AuthApi = client(null)

    fun me(accessToken: String): MeApi = client(accessToken)

    fun signIn(
        email: String,
        mail: RecordingMailSender,
    ): AuthTokens {
        assertEquals(HttpStatus.ACCEPTED, auth.startEmailSignIn(EmailCodeStartRequest(email)).statusCode)
        return requireNotNull(auth.verifyEmailSignIn(EmailCodeVerifyRequest(email, mail.lastCodeFor(email))).body)
    }

    private inline fun <reified T : Any> client(accessToken: String?): T {
        val rest =
            RestClient
                .builder()
                .baseUrl("http://localhost:$port${AuthApi.BASE_PATH}")
                .defaultHeader("X-Platform", platform)
                .defaultHeader("X-App-Version", appVersion)
                .apply { b -> accessToken?.let { b.defaultHeader("Authorization", "Bearer $it") } }
                .configureMessageConverters { it.withJsonConverter(JacksonJsonHttpMessageConverter(strict)) }
                .build()
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rest)).build().createClient(T::class.java)
    }

    companion object {
        /** Runs [call], expects an RFC 9457 error with [status], and returns its body read as the spec's `Problem`. */
        fun expectProblem(
            status: HttpStatus,
            call: () -> Any?,
        ): Problem {
            val e = assertFailsWith<RestClientResponseException> { call() }
            assertEquals(status.value(), e.statusCode.value())
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, e.responseHeaders?.contentType)
            val problem = requireNotNull(e.getResponseBodyAs(Problem::class.java))
            assertEquals(status.value(), problem.status)
            return problem
        }
    }
}
