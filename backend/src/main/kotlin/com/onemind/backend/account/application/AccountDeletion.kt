package com.onemind.backend.account.application

import com.onemind.backend.account.infra.UserAccountRepository
import com.onemind.backend.common.security.AuthenticationRequiredException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * In-app account deletion (ADR 0004; App Store 5.1.1(v), GDPR art. 17). The user and their personal
 * data go immediately: identities, refresh tokens, memberships and followed venues cascade. An
 * organization left without an owner is scheduled for deletion after [ORGANIZATION_GRACE]
 * (`docs/architecture/DB_SCHEMA.md`); until then support can reverse an accidental deletion.
 */
@Service
class AccountDeletion(
    private val users: UserAccountRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun delete(userId: UUID) {
        transactions.executeWithoutResult {
            val now = clock.instant()
            val organizations = users.scheduleDeletionOfOrganizationsOwnedOnlyBy(userId, now + ORGANIZATION_GRACE, now)
            // A valid access token of a user deleted a moment ago.
            if (!users.delete(userId)) throw AuthenticationRequiredException()
            log.info("Account deleted: userId={}, organizationsScheduledForDeletion={}", userId, organizations)
        }
    }

    companion object {
        val ORGANIZATION_GRACE: Duration = Duration.ofDays(30)
    }
}
