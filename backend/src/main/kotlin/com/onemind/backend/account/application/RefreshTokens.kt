package com.onemind.backend.account.application

import com.onemind.backend.account.infra.persistence.RefreshTokenRepository
import com.onemind.backend.common.security.AccessTokenIssuer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Exchanges a refresh token for new tokens (ADR 0004): every refresh rotates the token inside its
 * family; presenting a used token revokes the whole family (theft detection).
 */
@Service
class RefreshTokens(
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
                        val expiresAt = now + StartSession.REFRESH_TOKEN_TTL
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
}
