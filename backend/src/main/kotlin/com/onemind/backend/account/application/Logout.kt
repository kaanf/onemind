package com.onemind.backend.account.application

import com.onemind.backend.account.infra.persistence.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

@Service
class Logout(
    private val refreshTokens: RefreshTokenRepository,
    private val secrets: AuthSecrets,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Signs a device out: revokes the family of [refreshToken]. An unknown token does nothing, so
     * the app can repeat the call.
     */
    fun signOut(refreshToken: String) {
        transactions.executeWithoutResult {
            refreshTokens.findFamilyId(secrets.hashRefreshToken(refreshToken))?.let { familyId ->
                refreshTokens.lockFamily(familyId)
                val revoked = refreshTokens.revokeFamily(familyId, clock.instant())
                log.info("Signed out: familyId={}, revokedTokens={}", familyId, revoked)
            }
        }
    }
}
