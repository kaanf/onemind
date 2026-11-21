package com.onemind.backend.common.security

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Access-token keys and claims (ADR 0004). Keys are EC P-256 JWKs (ES256) with a `kid`, from the
 * environment; generate one with `./gradlew generateJwtKey`.
 *
 * @param signingKey private JWK (JSON) that signs new access tokens.
 * @param previousPublicKey public JWK (JSON) still accepted during a key rotation, until the
 *   tokens it signed have expired.
 */
@ConfigurationProperties("onemind.security.jwt")
data class JwtProperties(
    val issuer: String,
    val audience: String,
    val accessTokenTtl: Duration,
    val signingKey: String? = null,
    val previousPublicKey: String? = null,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig(
    private val properties: JwtProperties,
    environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val signingKey: ECKey =
        properties.signingKey?.takeIf { it.isNotBlank() }?.let(ECKey::parse)
            ?: run {
                check(!environment.acceptsProfiles(Profiles.of("prod"))) { "onemind.security.jwt.signing-key is required in prod" }
                log.warn("No JWT signing key configured: using a temporary key, tokens end with this process")
                generateKey()
            }

    init {
        require(signingKey.isPrivate && signingKey.curve == Curve.P_256) { "The JWT signing key must be a private P-256 JWK" }
        requireNotNull(signingKey.keyID) { "The JWT signing key needs a kid" }
    }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(signingKey)))

    @Bean
    fun jwtDecoder(clock: Clock): JwtDecoder {
        val verificationKeys =
            listOfNotNull(signingKey.toPublicJWK(), properties.previousPublicKey?.takeIf { it.isNotBlank() }?.let(ECKey::parse))
        return NimbusJwtDecoder
            .withJwkSource(ImmutableJWKSet(JWKSet(verificationKeys)))
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtTimestampValidator().apply { setClock(clock) },
                        JwtIssuerValidator(properties.issuer),
                        audienceValidator(properties.audience),
                    ),
                )
            }
    }

    @Bean
    fun accessTokenIssuer(
        encoder: JwtEncoder,
        clock: Clock,
    ) = AccessTokenIssuer(encoder, properties, signingKey.keyID, clock)

    private fun audienceValidator(audience: String) =
        OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.audience?.contains(audience) == true) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "The aud claim is not valid", null))
            }
        }

    companion object {
        fun generateKey(keyId: String = UUID.randomUUID().toString()): ECKey =
            ECKeyGenerator(Curve.P_256).keyUse(KeyUse.SIGNATURE).keyID(keyId).generate()
    }
}
