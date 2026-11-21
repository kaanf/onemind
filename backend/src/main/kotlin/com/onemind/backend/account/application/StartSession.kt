package com.onemind.backend.account.application

import com.onemind.backend.account.infra.persistence.RefreshTokenRepository
import com.onemind.backend.common.security.AccessToken
import com.onemind.backend.common.security.AccessTokenIssuer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SessionTokens(
    val accessToken: AccessToken,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

/**
 * Starts the session of a signed-in device (ADR 0004): a new refresh token family and an access
 * token. Used by the sign-in use cases inside their transaction.
 */
@Component
class StartSession(
    private val refreshTokens: RefreshTokenRepository,
    private val accessTokens: AccessTokenIssuer,
    private val secrets: AuthSecrets,
) {
    /** Call inside the sign-in transaction. */
    fun start(
        userId: UUID,
        deviceName: String?,
        now: Instant,
    ): SessionTokens {
        val refreshToken = secrets.newRefreshToken()
        val expiresAt = now + REFRESH_TOKEN_TTL
        refreshTokens.insert(userId, UUID.randomUUID(), secrets.hashRefreshToken(refreshToken), expiresAt, deviceName, now)
        return SessionTokens(accessTokens.issue(userId), refreshToken, expiresAt)
    }

    companion object {
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}
