package com.onemind.backend.common.security

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class AccessToken(
    val value: String,
    val expiresAt: Instant,
)

/**
 * Signs access tokens (ADR 0004): `sub` is the user ID, so the authenticated principal's name is
 * that UUID everywhere (`IdempotencyFilter` relies on it). Roles and venue access are not in the
 * token; they are loaded per request.
 */
class AccessTokenIssuer(
    private val encoder: JwtEncoder,
    private val properties: JwtProperties,
    private val keyId: String,
    private val clock: Clock,
) {
    fun issue(userId: UUID): AccessToken {
        val now = clock.instant()
        val expiresAt = now + properties.accessTokenTtl
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.issuer)
                .audience(listOf(properties.audience))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build()
        val header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(keyId).build()
        return AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresAt)
    }
}
