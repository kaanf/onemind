package com.onemind.backend.account

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.application.IdTokenSignIn
import com.onemind.backend.testing.MutableClock
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Apple and Google sign-in against a local JWKS server standing in for the providers' keys. */
@Import(TestcontainersConfiguration::class, IdTokenSignInApiTests.Config::class)
@SpringBootTest
@AutoConfigureMockMvc
class IdTokenSignInApiTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(NOW)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private val json = JsonMapper.builder().build()

    private val email = "owner-${UUID.randomUUID()}@example.com"
    private val gmail = "owner.${UUID.randomUUID()}@gmail.com"
    private val subject = "sub-${UUID.randomUUID()}"
    private val nonce = "nonce-${UUID.randomUUID()}"
    private val ip = "10.2.${IP_COUNTER.incrementAndGet() / 250}.${IP_COUNTER.get() % 250 + 1}"

    // Apple

    @Test
    fun `apple creates the account with the verified email, the name and an APPLE identity`() {
        val tokens = apple(appleToken()).andExpect { status { isOk() } }.body()

        assertNotNull(tokens["accessToken"].asString())
        val userId = userIdByEmail()
        assertEquals("Ada Owner", jdbc.queryForObject<String>("select display_name from account.app_user where id = ?", userId))
        assertEquals(userId, identityUser("APPLE"))
    }

    @Test
    fun `apple signs in to the same user by subject, also without an email in the token`() {
        apple(appleToken()).andExpect { status { isOk() } }
        val userId = userIdByEmail()

        apple(appleToken(email = null, emailVerified = null)).andExpect { status { isOk() } }

        assertEquals(userId, identityUser("APPLE"))
        assertEquals(1, jdbc.queryForObject<Int>("select count(*) from account.user_identity where user_id = ?", userId))
    }

    @Test
    fun `apple links an existing account with the same verified email and keeps its name`() {
        val existing =
            jdbc.queryForObject<UUID>(
                "insert into account.app_user (email, email_verified_at, display_name) values (?, now(), 'Kept') returning id",
                email.uppercase(),
            )

        apple(appleToken()).andExpect { status { isOk() } }

        assertEquals(existing, identityUser("APPLE"))
        assertEquals("Kept", jdbc.queryForObject<String>("select display_name from account.app_user where id = ?", existing))
    }

    @Test
    fun `apple accepts email_verified as the string true`() {
        apple(appleToken(emailVerified = "true")).andExpect { status { isOk() } }
    }

    @Test
    fun `apple rejects a token whose nonce is not the hash of the raw nonce`() {
        apple(appleToken(tokenNonce = nonce)).andExpectProblem(401, "ID_TOKEN_INVALID")
        apple(appleToken(), rawNonce = "other-$nonce").andExpectProblem(401, "ID_TOKEN_INVALID")
    }

    @Test
    fun `apple rejects tokens with a wrong audience, issuer, expiry or key`() {
        apple(appleToken(audience = "com.someone.else")).andExpectProblem(401, "ID_TOKEN_INVALID")
        apple(appleToken(issuer = "https://accounts.google.com")).andExpectProblem(401, "ID_TOKEN_INVALID")
        apple(appleToken(expiresAt = NOW - Duration.ofMinutes(5))).andExpectProblem(401, "ID_TOKEN_INVALID")
        apple(appleToken(key = OTHER_KEY)).andExpectProblem(401, "ID_TOKEN_INVALID")
        apple("not-a-jwt").andExpectProblem(401, "ID_TOKEN_INVALID")
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.user_identity where subject = ?", subject))
    }

    @Test
    fun `a new identity without a verified email is refused and creates nothing`() {
        apple(appleToken(emailVerified = false)).andExpectProblem(422, "EMAIL_NOT_VERIFIED")

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.app_user where email = ?", email))
    }

    // Google

    @Test
    fun `google accepts both issuer spellings and links a gmail address to the email-code account`() {
        val existing =
            jdbc.queryForObject<UUID>("insert into account.app_user (email, email_verified_at) values (?, now()) returning id", gmail)

        google(googleToken(issuer = "accounts.google.com")).andExpect { status { isOk() } }
        google(googleToken(issuer = "https://accounts.google.com")).andExpect { status { isOk() } }

        assertEquals(existing, identityUser("GOOGLE"))
    }

    @Test
    fun `google links a workspace address only within the token's hosted domain`() {
        val existing =
            jdbc.queryForObject<UUID>("insert into account.app_user (email, email_verified_at) values (?, now()) returning id", email)

        google(googleToken(email = email, hostedDomain = "other.com")).andExpectProblem(422, "EMAIL_NOT_VERIFIED")
        google(googleToken(email = email, hostedDomain = "Example.com")).andExpect { status { isOk() } }

        assertEquals(existing, identityUser("GOOGLE"))
    }

    @Test
    fun `google never links or creates by an address google does not vouch for`() {
        val existing =
            jdbc.queryForObject<UUID>("insert into account.app_user (email, email_verified_at) values (?, now()) returning id", email)

        google(googleToken(email = email)).andExpectProblem(422, "EMAIL_NOT_VERIFIED")

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.user_identity where user_id = ?", existing))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.user_identity where subject = ?", subject))
    }

    @Test
    fun `google signs in an already linked subject without a trusted email`() {
        google(googleToken()).andExpect { status { isOk() } }
        val userId = identityUser("GOOGLE")

        google(googleToken(email = email)).andExpect { status { isOk() } }

        assertEquals(userId, identityUser("GOOGLE"))
    }

    @Test
    fun `unsigned, HMAC-signed and malformed tokens are rejected`() {
        val claims = claims("https://appleid.apple.com", APPLE_CLIENT_ID, email, true, sha256Hex(nonce), NOW + Duration.ofMinutes(10))
        val unsigned = PlainJWT(claims).serialize()
        val hmac =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY.keyID).build(), claims)
                .apply { sign(MACSigner(ByteArray(32) { 7 })) }
                .serialize()
        val noneWithSignature = unsigned.substringBeforeLast('.') + "." + appleToken().substringAfterLast('.')

        listOf(unsigned, hmac, noneWithSignature, "a.b.c", appleToken().dropLast(4) + "AAAA").forEach {
            apple(it).andExpectProblem(401, "ID_TOKEN_INVALID")
        }
    }

    @Test
    fun `google checks the nonce only when the app sends one`() {
        google(googleToken(tokenNonce = null), sentNonce = null).andExpect { status { isOk() } }
        google(googleToken(tokenNonce = nonce), sentNonce = nonce).andExpect { status { isOk() } }
        google(googleToken(tokenNonce = null), sentNonce = nonce).andExpectProblem(401, "ID_TOKEN_INVALID")
        google(googleToken(tokenNonce = "other-$nonce"), sentNonce = nonce).andExpectProblem(401, "ID_TOKEN_INVALID")
    }

    @Test
    fun `an apple token is not accepted as a google token`() {
        google(appleToken()).andExpectProblem(401, "ID_TOKEN_INVALID")
    }

    @Test
    fun `id token sign-ins are limited per IP`() {
        repeat(IdTokenSignIn.LIMIT_PER_IP) { apple("not-a-jwt").andExpectProblem(401, "ID_TOKEN_INVALID") }

        apple(appleToken()).andExpectProblem(429, "RATE_LIMITED")
    }

    private fun apple(
        identityToken: String,
        rawNonce: String = nonce,
    ) = post(
        "/api/v1/auth/apple",
        mapOf("identityToken" to identityToken, "nonce" to rawNonce, "displayName" to " Ada Owner ", "deviceName" to "Test phone"),
    )

    private fun google(
        idToken: String,
        sentNonce: String? = null,
    ) = post("/api/v1/auth/google", mapOf("idToken" to idToken, "nonce" to sentNonce, "deviceName" to "Test phone"))

    private fun post(
        path: String,
        body: Map<String, String?>,
    ) = mockMvc.post(path) {
        with { it.apply { remoteAddr = ip } }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(body.filterValues { it != null })
    }

    private fun appleToken(
        email: String? = this.email,
        emailVerified: Any? = true,
        tokenNonce: String? = sha256Hex(nonce),
        audience: String = APPLE_CLIENT_ID,
        issuer: String = "https://appleid.apple.com",
        expiresAt: Instant = NOW + Duration.ofMinutes(10),
        key: RSAKey = KEY,
    ) = idToken(issuer, audience, email, emailVerified, tokenNonce, expiresAt, key)

    private fun googleToken(
        issuer: String = "https://accounts.google.com",
        tokenNonce: String? = null,
        email: String = gmail,
        hostedDomain: String? = null,
    ) = idToken(issuer, GOOGLE_CLIENT_ID, email, true, tokenNonce, NOW + Duration.ofMinutes(10), KEY, hostedDomain)

    private fun idToken(
        issuer: String,
        audience: String,
        email: String?,
        emailVerified: Any?,
        nonce: String?,
        expiresAt: Instant,
        key: RSAKey,
        hostedDomain: String? = null,
    ): String =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
            claims(issuer, audience, email, emailVerified, nonce, expiresAt, hostedDomain),
        ).apply { sign(RSASSASigner(key)) }
            .serialize()

    private fun claims(
        issuer: String,
        audience: String,
        email: String?,
        emailVerified: Any?,
        nonce: String?,
        expiresAt: Instant,
        hostedDomain: String? = null,
    ): JWTClaimsSet =
        JWTClaimsSet
            .Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(NOW - Duration.ofMinutes(1)))
            .expirationTime(Date.from(expiresAt))
            .apply {
                email?.let { claim("email", it) }
                emailVerified?.let { claim("email_verified", it) }
                nonce?.let { claim("nonce", it) }
                hostedDomain?.let { claim("hd", it) }
            }.build()

    private fun userIdByEmail(): UUID? = jdbc.queryForObject<UUID>("select id from account.app_user where email = ?", email)

    private fun identityUser(provider: String): UUID? =
        jdbc.queryForObject<UUID>("select user_id from account.user_identity where provider = ? and subject = ?", provider, subject)

    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun ResultActionsDsl.body(): JsonNode = json.readTree(andReturn().response.contentAsString)

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.code") { value(code) }
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-11-21T12:00:00Z")
        private const val APPLE_CLIENT_ID = "app.onemind.ios"
        private const val GOOGLE_CLIENT_ID = "onemind-web.apps.googleusercontent.com"
        private val KEY: RSAKey = RSAKeyGenerator(2048).keyID("provider-key").generate()
        private val OTHER_KEY: RSAKey = RSAKeyGenerator(2048).keyID("provider-key").generate()
        private val IP_COUNTER = AtomicInteger()

        // Serves the public key the way Apple and Google serve their JWKS.
        private val jwksServer: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                val body = JWKSet(KEY.toPublicJWK()).toString().toByteArray()
                createContext("/keys") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun idTokenProperties(registry: DynamicPropertyRegistry) {
            val keys = "http://127.0.0.1:${jwksServer.address.port}/keys"
            registry.add("onemind.auth.id-token.apple.jwk-set-uri") { keys }
            registry.add("onemind.auth.id-token.apple.client-ids") { "$APPLE_CLIENT_ID,app.onemind.android" }
            registry.add("onemind.auth.id-token.google.jwk-set-uri") { keys }
            registry.add("onemind.auth.id-token.google.client-ids") { GOOGLE_CLIENT_ID }
        }
    }
}
