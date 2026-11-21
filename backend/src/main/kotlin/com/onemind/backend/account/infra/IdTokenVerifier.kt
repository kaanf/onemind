package com.onemind.backend.account.infra

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Clock
import java.time.Duration

/**
 * Where an identity provider's ID tokens come from and who they must be for.
 *
 * @param clientIds accepted `aud` values (bundle/service IDs for Apple, OAuth client IDs for Google).
 *   Empty: every token of this provider is rejected.
 */
data class IdTokenIssuerProperties(
    val jwkSetUri: String,
    val issuers: List<String>,
    val clientIds: List<String> = emptyList(),
)

@ConfigurationProperties("onemind.auth.id-token")
data class IdTokenProperties(
    val apple: IdTokenIssuerProperties,
    val google: IdTokenIssuerProperties,
)

/** The claims of a valid ID token that sign-in uses. */
data class VerifiedIdToken(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val nonce: String?,
)

/**
 * Verifies Apple and Google ID tokens (ADR 0004): RS256 signature against the provider's JWKS
 * (cached by Nimbus, refetched for an unknown `kid`), `iss`, `aud` and expiry with the app clock.
 * The nonce is checked by the caller, which knows how each provider hashes it.
 */
@Component
@EnableConfigurationProperties(IdTokenProperties::class)
class IdTokenVerifier(
    properties: IdTokenProperties,
    clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val decoders: Map<IdentityProvider, JwtDecoder?> =
        mapOf(
            IdentityProvider.APPLE to decoder(IdentityProvider.APPLE, properties.apple, clock),
            IdentityProvider.GOOGLE to decoder(IdentityProvider.GOOGLE, properties.google, clock),
        )

    /** The token's claims, or `null` if it is not a valid token of [provider] for this app. */
    fun verify(
        provider: IdentityProvider,
        token: String,
    ): VerifiedIdToken? {
        val decoder = decoders[provider] ?: return null
        val jwt =
            try {
                decoder.decode(token)
            } catch (e: BadJwtException) {
                // Other JwtExceptions (JWKS unreachable) are not the token's fault and propagate.
                // Type only: messages can quote claims.
                log.info("Rejected an ID token: provider={}, reason={}", provider, e.javaClass.simpleName)
                return null
            }
        val subject = jwt.subject?.takeIf { it.isNotBlank() } ?: return null
        val emailVerified = jwt.claims["email_verified"].let { it == true || it == "true" }
        return VerifiedIdToken(subject, jwt.getClaimAsString("email"), emailVerified, jwt.getClaimAsString("nonce"))
    }

    private fun decoder(
        provider: IdentityProvider,
        properties: IdTokenIssuerProperties,
        clock: Clock,
    ): JwtDecoder? {
        if (properties.clientIds.isEmpty()) {
            log.warn("No client IDs configured for {} sign-in: its tokens are rejected", provider)
            return null
        }
        val http =
            RestTemplate(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(JWKS_TIMEOUT)
                    setReadTimeout(JWKS_TIMEOUT)
                },
            )
        return NimbusJwtDecoder
            .withJwkSetUri(properties.jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(http)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtTimestampValidator().apply { setClock(clock) },
                        claimValidator("exp") { jwt -> jwt.expiresAt != null },
                        claimValidator("iss") { jwt -> jwt.getClaimAsString("iss") in properties.issuers },
                        claimValidator("aud") { jwt -> jwt.audience.orEmpty().any { it in properties.clientIds } },
                    ),
                )
            }
    }

    private fun claimValidator(
        claim: String,
        valid: (Jwt) -> Boolean,
    ) = OAuth2TokenValidator<Jwt> { jwt ->
        if (valid(jwt)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "The $claim claim is not valid", null))
        }
    }

    private companion object {
        val JWKS_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
