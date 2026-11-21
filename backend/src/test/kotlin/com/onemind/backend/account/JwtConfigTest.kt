package com.onemind.backend.account

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.onemind.backend.common.security.JwtConfig
import com.onemind.backend.common.security.JwtProperties
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Access-token keys and expiry (ADR 0004): required in prod, P-256 with a kid, rotation without sign-outs, no clock skew. */
class JwtConfigTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val local = MockEnvironment()

    @Test
    fun `prod requires a signing key`() {
        assertFailsWith<IllegalStateException> { JwtConfig(properties(), MockEnvironment().apply { setActiveProfiles("prod") }) }
    }

    @Test
    fun `the signing key must be a private P-256 key with a kid`() {
        val publicOnly = JwtConfig.generateKey().toPublicJWK().toJSONString()
        val otherCurve = ECKeyGenerator(Curve.P_384).keyID("k").generate().toJSONString()
        val noKid = ECKeyGenerator(Curve.P_256).generate().toJSONString()

        listOf(publicOnly, otherCurve, noKid).forEach { key ->
            assertFailsWith<IllegalArgumentException> { JwtConfig(properties(signingKey = key), local) }
        }
    }

    @Test
    fun `during a rotation tokens of the previous key are still accepted, others are not`() {
        val previous = JwtConfig.generateKey("previous")
        val current = JwtConfig.generateKey("current")
        val decoder =
            JwtConfig(
                properties(signingKey = current.toJSONString(), previousPublicKey = previous.toPublicJWK().toJSONString()),
                local,
            ).jwtDecoder(clock)

        assertEquals("user", decoder.decode(sign(previous)).subject)
        assertEquals("user", decoder.decode(sign(current)).subject)
        assertFailsWith<BadJwtException> { decoder.decode(sign(JwtConfig.generateKey("stranger"))) }
    }

    @Test
    fun `an access token is accepted until its expiry and rejected 1 s after (no clock skew)`() {
        val key = JwtConfig.generateKey("current")
        val token = sign(key)
        val expiresAt = now + Duration.ofMinutes(15)

        fun decoderAt(at: Instant) =
            JwtConfig(properties(signingKey = key.toJSONString()), local).jwtDecoder(Clock.fixed(at, ZoneOffset.UTC))

        assertEquals("user", decoderAt(expiresAt - Duration.ofSeconds(1)).decode(token).subject)
        assertFailsWith<BadJwtException> { decoderAt(expiresAt + Duration.ofSeconds(1)).decode(token) }
    }

    private fun properties(
        signingKey: String? = null,
        previousPublicKey: String? = null,
    ) = JwtProperties(ISSUER, AUDIENCE, Duration.ofMinutes(15), signingKey, previousPublicKey)

    private fun sign(key: com.nimbusds.jose.jwk.ECKey): String {
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(ISSUER)
                .audience(listOf(AUDIENCE))
                .subject("user")
                .issuedAt(now)
                .expiresAt(now + Duration.ofMinutes(15))
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(key.keyID).build()
        return NimbusJwtEncoder(ImmutableJWKSet(JWKSet(key))).encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private companion object {
        const val ISSUER = "https://api.onemind.app"
        const val AUDIENCE = "onemind-app"
    }
}
