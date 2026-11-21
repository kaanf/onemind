package com.onemind.backend.account.application

import com.onemind.backend.account.domain.IdentityProvider
import com.onemind.backend.account.infra.persistence.EmailLoginCodeRepository
import com.onemind.backend.account.infra.persistence.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Clock

/**
 * Step 2 of passwordless sign-in (ADR 0004, screen A2): checks the latest code of the address,
 * counts wrong attempts (5 burn the code), then signs in, creating the account on first sign-in.
 */
@Service
class VerifyEmailSignIn(
    private val codes: EmailLoginCodeRepository,
    private val users: UserAccountRepository,
    private val startSession: StartSession,
    private val secrets: AuthSecrets,
    private val rateLimits: AuthRateLimits,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private sealed interface Verification {
        data class SignedIn(
            val tokens: SessionTokens,
        ) : Verification

        data class Rejected(
            val exception: RuntimeException,
        ) : Verification
    }

    fun verify(
        email: String,
        code: String,
        deviceName: String?,
        ip: InetAddress?,
    ): SessionTokens {
        val address = email.trim()
        rateLimits.limitIp("auth-email-verify", ip, VERIFY_LIMIT_PER_IP)
        // A wrong code must count even though the request fails, so the transaction returns the outcome.
        val verification =
            transactions.execute {
                val now = clock.instant()
                val latest = codes.latest(address, forUpdate = true)
                when {
                    latest == null -> {
                        Verification.Rejected(EmailCodeInvalidException(attemptsLeft = null))
                    }

                    latest.consumedAt != null || latest.expiresAt <= now -> {
                        Verification.Rejected(EmailCodeExpiredException())
                    }

                    latest.attempts >= MAX_ATTEMPTS -> {
                        Verification.Rejected(EmailCodeAttemptsExceededException())
                    }

                    !secrets.emailCodeMatches(address, code, latest.codeHash) -> {
                        codes.incrementAttempts(latest.id)
                        val attemptsLeft = MAX_ATTEMPTS - latest.attempts - 1
                        Verification.Rejected(
                            if (attemptsLeft == 0) EmailCodeAttemptsExceededException() else EmailCodeInvalidException(attemptsLeft),
                        )
                    }

                    else -> {
                        codes.consume(latest.id, now)
                        val userId = users.findOrCreateByVerifiedEmail(address, now)
                        users.linkIdentity(userId, IdentityProvider.EMAIL, address.lowercase(), address, now)
                        log.info("Signed in with an email code: userId={}", userId)
                        Verification.SignedIn(startSession.start(userId, deviceName, now))
                    }
                }
            }
        return when (verification) {
            is Verification.SignedIn -> verification.tokens
            is Verification.Rejected -> throw verification.exception
            null -> error("transaction returned no outcome")
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val VERIFY_LIMIT_PER_IP = 30
    }
}
