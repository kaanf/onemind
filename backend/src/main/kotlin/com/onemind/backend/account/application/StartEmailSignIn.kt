package com.onemind.backend.account.application

import com.onemind.backend.account.infra.persistence.EmailLoginCodeRepository
import com.onemind.backend.common.email.EmailMessage
import com.onemind.backend.common.email.EmailSender
import com.onemind.backend.common.error.RateLimitedException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.util.Locale

/**
 * Step 1 of passwordless sign-in (ADR 0004, screen A1): sends a 6-digit code valid for 10 minutes;
 * only the latest code of an address counts, a new one can be requested after 30 s. Requests are
 * limited per address (database) and per IP. Starting never reveals whether an account exists.
 */
@Service
class StartEmailSignIn(
    private val codes: EmailLoginCodeRepository,
    private val secrets: AuthSecrets,
    private val emailSender: EmailSender,
    private val messages: MessageSource,
    private val rateLimits: AuthRateLimits,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun start(
        email: String,
        ip: InetAddress?,
    ) {
        val address = email.trim()
        rateLimits.limitIp("auth-email-start", ip, START_LIMIT_PER_IP)
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

    companion object {
        val CODE_TTL: Duration = Duration.ofMinutes(10)
        val RESEND_AFTER: Duration = Duration.ofSeconds(30)
        const val CODE_LIMIT_PER_EMAIL = 5
        val CODE_LIMIT_WINDOW: Duration = Duration.ofHours(1)
        const val START_LIMIT_PER_IP = 10

        // English until the Czech translation (phase 7); then the user's or request's locale.
        private val LOCALE = Locale.ENGLISH
    }
}
