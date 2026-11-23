package com.onemind.backend.common.web

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.security.AccessTokenIssuer
import com.onemind.backend.testing.TenancyFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * The app's HTTP client sends `Accept: application/json` on every request, also to endpoints whose
 * success response has no body: they must not answer `406` (`BodylessResponseMappingConfig`).
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class AcceptJsonApiTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    lateinit var handlerMapping: RequestMappingHandlerMapping

    // A fresh client IP, so the per-IP sign-in limit doesn't leak between test classes.
    private val ip = "10.9.${Random.nextInt(250)}.${Random.nextInt(1, 250)}"

    @Test
    fun `start email sign-in accepts application json`() {
        mockMvc
            .post("/api/v1/auth/email/start") {
                mobileHeaders()
                content = """{"email":"owner-${UUID.randomUUID()}@example.com"}"""
            }.andExpect { status { isAccepted() } }

        mockMvc
            .post("/api/v1/auth/email/start") {
                mobileHeaders()
                content = """{"email":"not an address"}"""
            }.andExpectProblem(400, "VALIDATION_FAILED")
    }

    @Test
    fun `logout accepts application json`() {
        mockMvc
            .post("/api/v1/auth/logout") {
                mobileHeaders()
                content = """{"refreshToken":"unknown-token"}"""
            }.andExpect { status { isNoContent() } }

        mockMvc
            .post("/api/v1/auth/logout") {
                mobileHeaders()
                content = "{}"
            }.andExpectProblem(400, "BAD_REQUEST")
    }

    @Test
    fun `delete me accepts application json`() {
        val fixture = TenancyFixture(jdbc, accessTokens)

        mockMvc.delete("/api/v1/me") { mobileHeaders() }.andExpectProblem(401, "UNAUTHORIZED")

        mockMvc
            .delete("/api/v1/me") {
                mobileHeaders()
                header("Authorization", "Bearer ${fixture.token(fixture.outsider)}")
            }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `every API operation accepts application json`() {
        val notAcceptable =
            handlerMapping.handlerMethods.keys
                .filter { info -> info.patternValues.any { it.startsWith(ApiPathConfig.BASE_PATH) } }
                .filter { info ->
                    val produces = info.producesCondition.producibleMediaTypes
                    produces.isNotEmpty() && produces.none { it.isCompatibleWith(MediaType.APPLICATION_JSON) }
                }
        assertTrue(handlerMapping.handlerMethods.keys.any { info -> info.patternValues.any { it == "/api/v1/me" } })
        assertTrue(notAcceptable.isEmpty(), "Answer 406 to Accept: application/json: $notAcceptable")
    }

    /** Exactly what the app's Ktor client sends. */
    private fun MockHttpServletRequestDsl.mobileHeaders() {
        with { it.apply { remoteAddr = ip } }
        contentType = MediaType.APPLICATION_JSON
        accept = MediaType.APPLICATION_JSON
        header("X-App-Version", "1.0")
        header("X-Platform", "ANDROID")
    }

    // The problem may come back as application/json or application/problem+json: both read as JSON.
    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        match { result ->
            val contentType = MediaType.parseMediaType(result.response.contentType.orEmpty())
            assertTrue(
                contentType.equalsTypeAndSubtype(MediaType.APPLICATION_JSON) ||
                    contentType.equalsTypeAndSubtype(MediaType.APPLICATION_PROBLEM_JSON),
            )
        }
        jsonPath("$.code") { value(code) }
    }
}
