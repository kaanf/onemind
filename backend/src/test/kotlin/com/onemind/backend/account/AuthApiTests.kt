package com.onemind.backend.account

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.email.EmailMessage
import com.onemind.backend.common.email.EmailSender
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class, AuthApiTests.Config::class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension::class)
class AuthApiTests {
    class MutableClock(
        @Volatile var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now += duration
        }
    }

    class RecordingEmailSender : EmailSender {
        val sent = CopyOnWriteArrayList<EmailMessage>()

        override fun send(message: EmailMessage) {
            sent += message
        }
    }

    @RestController
    class ProbeController {
        @GetMapping("/test/whoami")
        fun whoami(authentication: Authentication) = mapOf("name" to authentication.name)

        @GetMapping("/test/forbidden")
        fun forbidden(): Unit = throw AccessDeniedException("not for you")
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-11-21T12:00:00Z"))

        @Bean
        @Primary
        fun recordingEmailSender() = RecordingEmailSender()

        @Bean
        fun probeController() = ProbeController()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var emails: RecordingEmailSender

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var jwtEncoder: JwtEncoder

    @Autowired
    lateinit var jwtDecoder: JwtDecoder

    private val json = JsonMapper.builder().build()

    // A fresh address and client IP per test, so the per-email and per-IP limits don't leak between tests.
    private val email = "Owner-${UUID.randomUUID()}@Example.com"
    private val ip = "10.1.${IP_COUNTER.incrementAndGet() / 250}.${IP_COUNTER.get() % 250 + 1}"

    // Sign-in

    @Test
    fun `start sends a code and stores only its hash`() {
        start().andExpect { status { isAccepted() } }

        val code = lastCode()
        assertTrue(code.matches(Regex("[0-9]{6}")))
        val hash = jdbc.queryForObject<String>("select code_hash from account.email_login_code where email = ?", email)
        assertNotEquals(code, hash)
        assertFalse(hash.orEmpty().contains(code))
    }

    @Test
    fun `verify signs in, creates the account and issues ES256 tokens for the user`() {
        val tokens = signIn()

        val userId = jdbc.queryForObject<UUID>("select id from account.app_user where email = ?", email)
        assertNotNull(jdbc.queryForObject<Instant>("select email_verified_at from account.app_user where id = ?", userId))
        assertEquals(
            1,
            jdbc.queryForObject<Int>("select count(*) from account.user_identity where user_id = ? and provider = 'EMAIL'", userId),
        )

        val jwt = jwtDecoder.decode(tokens["accessToken"].asString())
        assertEquals(userId.toString(), jwt.subject)
        assertEquals("ES256", jwt.headers["alg"].toString())
        assertNotNull(jwt.headers["kid"])
        assertEquals(listOf("onemind-app"), jwt.audience)
        assertEquals(clock.now + Duration.ofMinutes(15), jwt.expiresAt)
        assertEquals(clock.now + Duration.ofDays(30), Instant.parse(tokens["refreshTokenExpiresAt"].asString()))

        // The authenticated principal's name is the user's UUID (IdempotencyFilter relies on it).
        mockMvc
            .get("/test/whoami") { bearer(tokens["accessToken"].asString()) }
            .andExpect { jsonPath("$.name") { value(userId.toString()) } }
    }

    @Test
    fun `signing in again with the same address, in any case, signs into the same account`() {
        signIn()
        clock.advance(Duration.ofMinutes(1))
        val again = signIn(address = email.lowercase())

        val userIds = jdbc.queryForList("select id from account.app_user where email = ?", UUID::class.java, email)
        assertEquals(1, userIds.size)
        assertEquals(userIds.single().toString(), jwtDecoder.decode(again["accessToken"].asString()).subject)
    }

    @Test
    fun `a new code can be requested after 30 s and replaces the previous one`() {
        start()
        val first = lastCode()

        start()
            .andExpectProblem(429, "EMAIL_CODE_RESEND_TOO_SOON")
            .andExpect {
                header { string("Retry-After", "30") }
                jsonPath("$.params.retryAfterSeconds") { value(30) }
            }

        clock.advance(Duration.ofSeconds(30))
        start().andExpect { status { isAccepted() } }
        val second = lastCode()

        if (first != second) verify(first).andExpectProblem(422, "EMAIL_CODE_INVALID")
        verify(second).andExpect { status { isOk() } }
    }

    @Test
    fun `an address gets at most 5 codes an hour`() {
        repeat(5) {
            start().andExpect { status { isAccepted() } }
            clock.advance(Duration.ofSeconds(31))
        }

        // Until the first of the five codes (5 × 31 s ago) is an hour old.
        start().andExpectProblem(429, "RATE_LIMITED").andExpect { header { string("Retry-After", "${3600 - 5 * 31}") } }
    }

    @Test
    fun `an IP may start 10 sign-ins in 10 minutes`() {
        repeat(10) { start(address = "ip-$it-$email").andExpect { status { isAccepted() } } }

        start(address = "ip-11-$email").andExpectProblem(429, "RATE_LIMITED")
    }

    @Test
    fun `wrong codes count, and the fifth burns the code`() {
        start()
        val code = lastCode()
        val wrong = ((code.toInt() + 1) % 1_000_000).toString().padStart(6, '0')

        verify(wrong)
            .andExpectProblem(422, "EMAIL_CODE_INVALID")
            .andExpect { jsonPath("$.params.attemptsLeft") { value(4) } }
        repeat(3) { verify(wrong).andExpectProblem(422, "EMAIL_CODE_INVALID") }
        verify(wrong).andExpectProblem(422, "EMAIL_CODE_ATTEMPTS_EXCEEDED")

        verify(code).andExpectProblem(422, "EMAIL_CODE_ATTEMPTS_EXCEEDED")
    }

    @Test
    fun `a code expires after 10 minutes`() {
        start()
        clock.advance(Duration.ofMinutes(10))

        verify(lastCode()).andExpectProblem(422, "EMAIL_CODE_EXPIRED")
    }

    @Test
    fun `a code can be used once`() {
        start()
        val code = lastCode()
        verify(code).andExpect { status { isOk() } }

        verify(code).andExpectProblem(422, "EMAIL_CODE_EXPIRED")
    }

    @Test
    fun `verifying without a code is invalid`() {
        verify("123456").andExpectProblem(422, "EMAIL_CODE_INVALID").andExpect { jsonPath("$.params") { doesNotExist() } }
    }

    @Test
    fun `malformed requests are validation errors`() {
        start(address = "not-an-email").andExpectProblem(400, "VALIDATION_FAILED")
        verify("12ab56").andExpectProblem(400, "VALIDATION_FAILED")
    }

    @Test
    fun `neither the address nor the code is logged`(output: CapturedOutput) {
        signIn()

        val logged =
            output.out
                .lines()
                .filter { it.startsWith("{") }
                .joinToString("\n")
        assertFalse(logged.contains(email, ignoreCase = true))
        assertFalse(logged.contains(lastCode()))
    }

    // Refresh

    @Test
    fun `refresh rotates the refresh token`() {
        val first = signIn()

        val second = refresh(first["refreshToken"].asString()).andExpect { status { isOk() } }.body()

        assertNotEquals(first["refreshToken"].asString(), second["refreshToken"].asString())
        assertEquals(
            jwtDecoder.decode(first["accessToken"].asString()).subject,
            jwtDecoder.decode(second["accessToken"].asString()).subject,
        )
    }

    @Test
    fun `reusing a refresh token revokes the whole session`() {
        val first = signIn()
        val second = refresh(first["refreshToken"].asString()).body()

        refresh(first["refreshToken"].asString()).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
        refresh(second["refreshToken"].asString()).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
    }

    @Test
    fun `a refresh token expires after 30 days`() {
        val tokens = signIn()
        clock.advance(Duration.ofDays(30))

        refresh(tokens["refreshToken"].asString()).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
    }

    @Test
    fun `token responses are never stored for idempotency replay`() {
        val tokens = signIn()
        val key = UUID.randomUUID()

        mockMvc
            .post("/api/v1/auth/refresh") {
                bearer(tokens["accessToken"].asString())
                header("Idempotency-Key", key.toString())
                contentType = MediaType.APPLICATION_JSON
                content = json.writeValueAsString(mapOf("refreshToken" to tokens["refreshToken"].asString()))
            }.andExpect { status { isOk() } }

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from platform.idempotency_record where key = ?", key))
    }

    @Test
    fun `an unknown refresh token is rejected`() {
        refresh("not-a-token").andExpectProblem(401, "REFRESH_TOKEN_INVALID")
    }

    // Logout

    @Test
    fun `logout revokes every token of the session, also the rotated ones`() {
        val first = signIn()
        val second = refresh(first["refreshToken"].asString()).body()

        logout(second["refreshToken"].asString()).andExpect { status { isNoContent() } }

        refresh(second["refreshToken"].asString()).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
        assertEquals(
            0,
            jdbc.queryForObject<Int>(
                "select count(*) from account.refresh_token where user_id = ? and revoked_at is null",
                userId(first),
            ),
        )
    }

    @Test
    fun `logout signs out only that device`() {
        val phone = signIn()
        clock.advance(Duration.ofSeconds(31))
        val tablet = signIn()

        logout(phone["refreshToken"].asString()).andExpect { status { isNoContent() } }

        refresh(tablet["refreshToken"].asString()).andExpect { status { isOk() } }
    }

    @Test
    fun `logout is 204 for an unknown or already revoked token`() {
        val tokens = signIn()

        logout(tokens["refreshToken"].asString()).andExpect { status { isNoContent() } }
        logout(tokens["refreshToken"].asString()).andExpect { status { isNoContent() } }
        logout("unknown-token").andExpect { status { isNoContent() } }
    }

    // Account deletion

    @Test
    fun `deleting the account needs an access token`() {
        mockMvc.delete("/api/v1/me").andExpectProblem(401, "UNAUTHORIZED")
    }

    @Test
    fun `deleting the account removes the user, identities and every session`() {
        val tokens = signIn()
        val userId = userId(tokens)

        mockMvc.delete("/api/v1/me") { bearer(tokens["accessToken"].asString()) }.andExpect { status { isNoContent() } }

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.app_user where id = ?", userId))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.user_identity where user_id = ?", userId))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.refresh_token where user_id = ?", userId))
        refresh(tokens["refreshToken"].asString()).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
        // The access token is still signed and unexpired, but its user is gone.
        mockMvc.delete("/api/v1/me") { bearer(tokens["accessToken"].asString()) }.andExpectProblem(401, "UNAUTHORIZED")
    }

    @Test
    fun `signing in again after deletion creates a new account`() {
        val before = userId(signIn())
        mockMvc.delete("/api/v1/me") { bearer(token(before.toString())) }.andExpect { status { isNoContent() } }
        clock.advance(Duration.ofSeconds(31))

        assertNotEquals(before, userId(signIn()))
    }

    @Test
    fun `an organization is scheduled for deletion in 30 days only when its last owner leaves`() {
        val userId = userId(signIn())
        val otherUser = jdbc.queryForObject<UUID>("insert into account.app_user (email) values (?) returning id", "co-$email")!!
        val soleOwned = organization(userId to "OWNER")
        val coOwned = organization(userId to "OWNER", otherUser to "OWNER")
        val staffOnly = organization(userId to "STAFF", otherUser to "OWNER")

        mockMvc.delete("/api/v1/me") { bearer(token(userId.toString())) }.andExpect { status { isNoContent() } }

        assertEquals(clock.now + Duration.ofDays(30), deletionDueAt(soleOwned))
        assertEquals(null, deletionDueAt(coOwned))
        assertEquals(null, deletionDueAt(staffOnly))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", userId))
    }

    // Access tokens

    @Test
    fun `a request without a token is a 401 problem with WWW-Authenticate`() {
        mockMvc
            .get("/test/whoami")
            .andExpectProblem(401, "UNAUTHORIZED")
            .andExpect { header { string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")) } }
    }

    @Test
    fun `an expired access token is rejected`() {
        val token = signIn()["accessToken"].asString()
        clock.advance(Duration.ofMinutes(17))

        mockMvc.get("/test/whoami") { bearer(token) }.andExpectProblem(401, "UNAUTHORIZED")
    }

    @Test
    fun `tokens with another audience or issuer are rejected`() {
        val userId = UUID.randomUUID().toString()

        mockMvc.get("/test/whoami") { bearer(token(userId, audience = "someone-else")) }.andExpectProblem(401, "UNAUTHORIZED")
        mockMvc.get("/test/whoami") { bearer(token(userId, issuer = "https://evil.example")) }.andExpectProblem(401, "UNAUTHORIZED")
        mockMvc.get("/test/whoami") { bearer(token(userId)) }.andExpect { status { isOk() } }
    }

    @Test
    fun `a token signed with another key is rejected`() {
        val forged =
            org.springframework.security.oauth2.jwt
                .NimbusJwtEncoder(
                    com.nimbusds.jose.jwk.source.ImmutableJWKSet(
                        com.nimbusds.jose.jwk
                            .JWKSet(
                                com.onemind.backend.common.security.JwtConfig
                                    .generateKey("forged"),
                            ),
                    ),
                ).encode(parameters(UUID.randomUUID().toString(), "onemind-app", "https://api.onemind.app", "forged"))
                .tokenValue

        mockMvc.get("/test/whoami") { bearer(forged) }.andExpectProblem(401, "UNAUTHORIZED")
    }

    @Test
    fun `access denied is a 403 problem`() {
        val token = signIn()["accessToken"].asString()

        mockMvc.get("/test/forbidden") { bearer(token) }.andExpectProblem(403, "FORBIDDEN")
    }

    // Helpers

    private fun start(address: String = email) =
        mockMvc.post("/api/v1/auth/email/start") {
            with { it.apply { remoteAddr = ip } }
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("email" to address))
        }

    private fun verify(
        code: String,
        address: String = email,
    ) = mockMvc.post("/api/v1/auth/email/verify") {
        with { it.apply { remoteAddr = ip } }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(mapOf("email" to address, "code" to code, "deviceName" to "Test phone"))
    }

    private fun refresh(token: String) =
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("refreshToken" to token))
        }

    private fun logout(token: String) =
        mockMvc.post("/api/v1/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("refreshToken" to token))
        }

    private fun userId(tokens: JsonNode): UUID = UUID.fromString(jwtDecoder.decode(tokens["accessToken"].asString()).subject)

    private fun organization(vararg members: Pair<UUID, String>): UUID {
        val id = jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Bar') returning id")!!
        members.forEach { (user, role) ->
            jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, ?)", id, user, role)
        }
        return id
    }

    private fun deletionDueAt(organizationId: UUID): Instant? =
        jdbc.queryForObject<Instant>("select deletion_due_at from account.organization where id = ?", organizationId)

    private fun signIn(address: String = email): JsonNode {
        start(address).andExpect { status { isAccepted() } }
        return verify(lastCode(address), address).andExpect { status { isOk() } }.body()
    }

    private fun lastCode(address: String = email): String =
        emails.sent
            .last { it.to == address }
            .text
            .let { Regex("\\b([0-9]{6})\\b").find(it)?.groupValues?.get(1) ?: error("no code in email") }

    private fun ResultActionsDsl.body(): JsonNode = json.readTree(andReturn().response.contentAsString)

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(token: String) =
        header("Authorization", "Bearer $token")

    private fun token(
        subject: String,
        audience: String = "onemind-app",
        issuer: String = "https://api.onemind.app",
    ): String = jwtEncoder.encode(parameters(subject, audience, issuer, kid = null)).tokenValue

    private fun parameters(
        subject: String,
        audience: String,
        issuer: String,
        kid: String?,
    ): JwtEncoderParameters {
        val claims =
            JwtClaimsSet
                .builder()
                .subject(subject)
                .audience(listOf(audience))
                .issuer(issuer)
                .issuedAt(clock.now)
                .expiresAt(clock.now + Duration.ofMinutes(5))
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.ES256).apply { kid?.let(::keyId) }.build()
        return JwtEncoderParameters.from(header, claims)
    }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.code") { value(code) }
        jsonPath("$.traceId") { exists() }
    }

    private companion object {
        val IP_COUNTER = AtomicInteger()
    }
}
