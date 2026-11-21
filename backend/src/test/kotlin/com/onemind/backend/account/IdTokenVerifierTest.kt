package com.onemind.backend.account

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.onemind.backend.account.domain.IdentityProvider
import com.onemind.backend.account.infra.client.IdTokenIssuerProperties
import com.onemind.backend.account.infra.client.IdTokenProperties
import com.onemind.backend.account.infra.client.IdTokenVerifier
import com.onemind.backend.account.infra.client.idTokenTimestampValidator
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdTokenVerifierTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val key = RSAKeyGenerator(2048).keyID("k").generate()

    @Test
    fun `a provider token's expiry allows 60 s of clock skew, not more`() {
        fun validAt(offset: Duration) =
            !idTokenTimestampValidator(clock)
                .validate(
                    Jwt
                        .withTokenValue("t")
                        .header("alg", "RS256")
                        .issuedAt(now - Duration.ofMinutes(10))
                        .expiresAt(now - offset)
                        .build(),
                ).hasErrors()

        assertTrue(validAt(Duration.ofSeconds(59)))
        assertFalse(validAt(Duration.ofSeconds(61)))
    }

    @Test
    fun `the verifier rejects a provider token 61 s past its expiry and accepts one 59 s past`() {
        val jwks =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                val body = JWKSet(key.toPublicJWK()).toString().toByteArray()
                createContext("/keys") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }
        try {
            val verifier = verifier(appleClientIds = listOf("app.onemind.ios"), jwkSetUri = "http://127.0.0.1:${jwks.address.port}/keys")

            assertNull(verifier.verify(IdentityProvider.APPLE, token(expiresAt = now.minusSeconds(61))))
            assertNotNull(verifier.verify(IdentityProvider.APPLE, token(expiresAt = now.minusSeconds(59))))
        } finally {
            jwks.stop(0)
        }
    }

    @Test
    fun `a provider without client IDs rejects every token without fetching keys`() {
        val verifier = verifier(appleClientIds = emptyList(), jwkSetUri = "http://127.0.0.1:1/never-called")

        assertNull(verifier.verify(IdentityProvider.APPLE, token()))
    }

    @Test
    fun `unreachable provider keys are an error, not an invalid token`() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val verifier = verifier(appleClientIds = listOf("app.onemind.ios"), jwkSetUri = "http://127.0.0.1:$closedPort/keys")

        val error = assertThrows<JwtException> { verifier.verify(IdentityProvider.APPLE, token()) }
        assertFalse(error is BadJwtException)
    }

    private fun verifier(
        appleClientIds: List<String>,
        jwkSetUri: String,
    ) = IdTokenVerifier(
        IdTokenProperties(
            apple = IdTokenIssuerProperties(jwkSetUri, listOf("https://appleid.apple.com"), appleClientIds),
            google = IdTokenIssuerProperties(jwkSetUri, listOf("https://accounts.google.com")),
        ),
        clock,
    )

    private fun token(expiresAt: Instant = now.plusSeconds(600)): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer("https://appleid.apple.com")
                .audience("app.onemind.ios")
                .subject("sub")
                .expirationTime(Date.from(expiresAt))
                .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }
            .serialize()
    }
}
