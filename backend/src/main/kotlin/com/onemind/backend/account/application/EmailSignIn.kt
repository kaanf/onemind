package com.onemind.backend.account.application

import com.onemind.backend.account.infra.EmailLoginCodeRepository
import com.onemind.backend.account.infra.UserAccountRepository
import com.onemind.backend.common.email.EmailMessage
import com.onemind.backend.common.email.EmailSender
import com.onemind.backend.common.error.RateLimitedException
import com.onemind.backend.common.ratelimit.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.util.Locale

/**
 * Passwordless sign-in with a 6-digit email code (ADR 0004, screens A1/A2): codes are valid
 * 10 minutes, only the latest code of an address counts, 5 wrong attempts burn it, a new one can be
 * requested after 30 s. Requests are limited per address (database) and per IP (memory). Starting
 * never reveals whether an account exists.
 */
@Service
class EmailSignIn(
    private val codes: EmailLoginCodeRepository,
    private val users: UserAccountRepository,
    private val sessions: SessionTokenService,
    private val secrets: AuthSecrets,
    private val emailSender: EmailSender,
    private val messages: MessageSource,
    private val rateLimiter: RateLimiter,
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

    fun start(
        email: String,
        ip: InetAddress?,
    ) {
        val address = email.trim()
        limitIp("auth-email-start", ip, START_LIMIT_PER_IP)
        val code =
            transactions.execute {
                codes.lockAddress(address)
                val now = clock.instant()
                codes.latest(address)?.let { last ->
                    val wait = Duration.between(now, last.createdAt + RESEND_AFTER)
                    if (!wait.isNegative && !wait.isZero) throw EmailCodeResendTooSoonException(wait)
                }
                // Rolling window: a new code is allowed once the oldest counted one is an hour old.
                codes.nthNewestCreatedSince(address, now - CODE_LIMIT_WINDOW, CODE_LIMIT_PER_EMAIL)?.let { oldestCounted ->
                    throw RateLimitedException(Duration.between(now, oldestCounted + CODE_LIMIT_WINDOW))
                }
                secrets.newEmailCode().also { codes.insert(address, secrets.hashEmailCode(address, it), now + CODE_TTL, ip, now) }
            } ?: error("transaction returned no code")
        // Outside the transaction (no external calls in it). A failure is logged; the user asks again.
        try {
            emailSender.send(
                EmailMessage(
                    to = address,
                    subject = messages.getMessage("auth.email-code.subject", null, LOCALE),
                    text = messages.getMessage("auth.email-code.text", arrayOf<Any>(code, CODE_TTL.toMinutes()), LOCALE),
                ),
            )
            log.info("Sign-in code sent")
        } catch (e: Exception) {
            // Type only: SMTP errors can echo the recipient address, which must not be logged.
            log.error("Could not send a sign-in code: {}", e.javaClass.name)
        }
    }

    fun verify(
        email: String,
        code: String,
        deviceName: String?,
        ip: InetAddress?,
    ): SessionTokens {
        val address = email.trim()
        limitIp("auth-email-verify", ip, VERIFY_LIMIT_PER_IP)
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
                        log.info("Signed in with an email code: userId={}", userId)
                        Verification.SignedIn(sessions.startSession(userId, deviceName, now))
                    }
                }
            }
        return when (verification) {
            is Verification.SignedIn -> verification.tokens
            is Verification.Rejected -> throw verification.exception
            null -> error("transaction returned no outcome")
        }
    }

    private fun limitIp(
        action: String,
        ip: InetAddress?,
        limit: Int,
    ) {
        if (ip == null) return
        rateLimiter.tryAcquire("$action:ip:${ip.hostAddress}", limit, IP_LIMIT_WINDOW)?.let { throw RateLimitedException(it) }
    }

    companion object {
        val CODE_TTL: Duration = Duration.ofMinutes(10)
        val RESEND_AFTER: Duration = Duration.ofSeconds(30)
        const val MAX_ATTEMPTS = 5
        const val CODE_LIMIT_PER_EMAIL = 5
        val CODE_LIMIT_WINDOW: Duration = Duration.ofHours(1)
        const val START_LIMIT_PER_IP = 10
        const val VERIFY_LIMIT_PER_IP = 30
        val IP_LIMIT_WINDOW: Duration = Duration.ofMinutes(10)

        // English until the Czech translation (phase 7); then the user's or request's locale.
        private val LOCALE = Locale.ENGLISH
    }
}
