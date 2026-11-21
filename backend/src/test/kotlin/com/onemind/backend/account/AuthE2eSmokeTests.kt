package com.onemind.backend.account

import com.onemind.api.v1.client.model.RefreshTokenRequest
import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.testing.ApiClient
import com.onemind.backend.testing.ApiClient.Companion.expectProblem
import com.onemind.backend.testing.MutableClock
import com.onemind.backend.testing.RecordingMailSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Smoke test of the auth flows end to end: the real app on a port with PostgreSQL, driven by the
 * client generated from `docs/api/openapi.yaml` (ADR 0001, ADR 0004). The email code is read from the
 * sent mail. Rule-by-rule auth tests live in [AuthApiTests]; the 426 flow in [AppVersionE2eSmokeTests].
 */
@Import(TestcontainersConfiguration::class, AuthE2eSmokeTests.Config::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthE2eSmokeTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-11-21T12:00:00Z"))

        @Bean
        @Primary
        fun recordingMailSender() = RecordingMailSender()
    }

    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var mail: RecordingMailSender

    @Autowired
    lateinit var mapper: JsonMapper

    lateinit var api: ApiClient

    private val email = "e2e-${UUID.randomUUID()}@example.com"

    @BeforeEach
    fun client() {
        api = ApiClient(port, mapper)
    }

    @Test
    fun `email code sign-in returns an access and a refresh token`() {
        val tokens = api.signIn(email, mail)

        assertEquals(clock.instant() + Duration.ofMinutes(15), tokens.accessTokenExpiresAt.toInstant())
        assertEquals(clock.instant() + Duration.ofDays(30), tokens.refreshTokenExpiresAt.toInstant())
    }

    @Test
    fun `refresh rotates the token, and reusing an old one revokes the whole family`() {
        val first = api.signIn(email, mail)

        val second = assertNotNull(api.auth.refreshTokens(RefreshTokenRequest(first.refreshToken)).body)
        assertNotEquals(first.refreshToken, second.refreshToken)

        val reused = expectProblem(HttpStatus.UNAUTHORIZED) { api.auth.refreshTokens(RefreshTokenRequest(first.refreshToken)) }
        assertEquals("REFRESH_TOKEN_INVALID", reused.code)
        val revoked = expectProblem(HttpStatus.UNAUTHORIZED) { api.auth.refreshTokens(RefreshTokenRequest(second.refreshToken)) }
        assertEquals("REFRESH_TOKEN_INVALID", revoked.code)
    }

    @Test
    fun `an expired or invalid access token gets 401, and a refreshed one works`() {
        val tokens = api.signIn(email, mail)
        // 15 min lifetime plus the validator's default 60 s clock skew.
        clock.advance(Duration.ofMinutes(17))

        val expired = expectProblem(HttpStatus.UNAUTHORIZED) { api.me(tokens.accessToken).deleteMe() }
        assertEquals("UNAUTHORIZED", expired.code)
        assertNotNull(expired.traceId)
        val invalid = expectProblem(HttpStatus.UNAUTHORIZED) { api.me(tokens.accessToken.dropLast(4) + "AAAA").deleteMe() }
        assertEquals("UNAUTHORIZED", invalid.code)

        val refreshed = assertNotNull(api.auth.refreshTokens(RefreshTokenRequest(tokens.refreshToken)).body)
        assertEquals(HttpStatus.NO_CONTENT, api.me(refreshed.accessToken).deleteMe().statusCode)
    }
}
