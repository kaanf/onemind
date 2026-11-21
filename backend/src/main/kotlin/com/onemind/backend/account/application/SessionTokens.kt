package com.onemind.backend.account.application

import com.onemind.backend.account.infra.RefreshTokenRepository
import com.onemind.backend.common.security.AccessToken
import com.onemind.backend.common.security.AccessTokenIssuer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SessionTokens(
    val accessToken: AccessToken,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

/**
 * Access and refresh tokens of a signed-in device (ADR 0004). Every sign-in starts a refresh token
 * family; every refresh rotates the token inside its family. Presenting a used token revokes the
 * whole family (theft detection).
 */
@Service
class SessionTokenService(
    private val refreshTokens: RefreshTokenRepository,
    private val accessTokens: AccessTokenIssuer,
    private val secrets: AuthSecrets,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private sealed interface Rotation {
        data class Rotated(
            val userId: UUID,
            val refreshToken: String,
            val expiresAt: Instant,
        ) : Rotation

        data object Invalid : Rotation

        data class Reused(
            val userId: UUID,
        ) : Rotation
    }

    /** Starts a new family for a sign-in. Call inside the sign-in transaction. */
    fun startSession(
        userId: UUID,
        deviceName: String?,
        now: Instant,
    ): SessionTokens {
        val refreshToken = secrets.newRefreshToken()
        val expiresAt = now + REFRESH_TOKEN_TTL
        refreshTokens.insert(userId, UUID.randomUUID(), secrets.hashRefreshToken(refreshToken), expiresAt, deviceName, now)
        return SessionTokens(accessTokens.issue(userId), refreshToken, expiresAt)
    }

    fun refresh(refreshToken: String): SessionTokens {
        // Decided in a transaction that commits also when the token is rejected (the family revocation).
        val rotation =
            transactions.execute {
                val hash = secrets.hashRefreshToken(refreshToken)
                refreshTokens.findFamilyId(hash)?.let(refreshTokens::lockFamily)
                val now = clock.instant()
                val current = refreshTokens.findByHashForUpdate(hash)
                when {
                    current == null || current.revokedAt != null || current.expiresAt <= now -> {
                        Rotation.Invalid
                    }

                    current.usedAt != null -> {
                        refreshTokens.revokeFamily(current.familyId, now)
                        Rotation.Reused(current.userId)
                    }

                    else -> {
                        val next = secrets.newRefreshToken()
                        val expiresAt = now + REFRESH_TOKEN_TTL
                        val nextId =
                            refreshTokens.insert(
                                current.userId,
                                current.familyId,
                                secrets.hashRefreshToken(next),
                                expiresAt,
                                current.deviceName,
                                now,
                            )
                        refreshTokens.markUsed(current.id, nextId, now)
                        Rotation.Rotated(current.userId, next, expiresAt)
                    }
                }
            }
        return when (rotation) {
            is Rotation.Rotated -> {
                SessionTokens(accessTokens.issue(rotation.userId), rotation.refreshToken, rotation.expiresAt)
            }

            is Rotation.Reused -> {
                log.warn("Refresh token reused, session revoked: userId={}", rotation.userId)
                throw RefreshTokenInvalidException()
            }

            Rotation.Invalid, null -> {
                throw RefreshTokenInvalidException()
            }
        }
    }

    /**
     * Signs a device out: revokes the family of [refreshToken]. An unknown token does nothing, so
     * the app can repeat the call.
     */
    fun logout(refreshToken: String) {
        transactions.executeWithoutResult {
            refreshTokens.findFamilyId(secrets.hashRefreshToken(refreshToken))?.let { familyId ->
                refreshTokens.lockFamily(familyId)
                val revoked = refreshTokens.revokeFamily(familyId, clock.instant())
                log.info("Signed out: familyId={}, revokedTokens={}", familyId, revoked)
            }
        }
    }

    companion object {
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}
