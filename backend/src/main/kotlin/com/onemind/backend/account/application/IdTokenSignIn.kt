package com.onemind.backend.account.application

import com.onemind.backend.account.domain.IdentityProvider
import com.onemind.backend.account.infra.client.IdTokenVerifier
import com.onemind.backend.account.infra.client.VerifiedIdToken
import com.onemind.backend.account.infra.persistence.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.InetAddress
import java.time.Clock

/**
 * Sign in with Apple or Google (ADR 0004, screen A1). The ID token is verified first, outside the
 * transaction (it may fetch the provider's keys). The user is found by the provider's subject, else
 * by the token's verified email (linking an existing account, also an email-code one), else created.
 * A token without a trusted email can only sign in to an identity that is already linked.
 *
 * Google's `email_verified` is authoritative only for Gmail and Workspace (`hd`) accounts: another
 * Google account can carry a verified-looking address it doesn't control, so its email never links
 * or creates an account (pre-hijack protection).
 */
@Service
class IdTokenSignIn(
    private val verifier: IdTokenVerifier,
    private val users: UserAccountRepository,
    private val startSession: StartSession,
    private val rateLimits: AuthRateLimits,
    private val secrets: AuthSecrets,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [rawNonce] is the value the app generated; Apple's token carries its SHA-256 (hex). */
    fun signInWithApple(
        identityToken: String,
        rawNonce: String,
        displayName: String?,
        deviceName: String?,
        ip: InetAddress?,
    ): SessionTokens {
        rateLimits.limitIp("auth-id-token", ip, LIMIT_PER_IP)
        val token = verify(IdentityProvider.APPLE, identityToken) { it == secrets.sha256Hex(rawNonce) }
        return signIn(IdentityProvider.APPLE, token, displayName?.trim()?.takeIf { it.isNotEmpty() }, deviceName)
    }

    /** Google puts the [nonce] in the token unchanged; it is checked only when the app sends one. */
    fun signInWithGoogle(
        idToken: String,
        nonce: String?,
        deviceName: String?,
        ip: InetAddress?,
    ): SessionTokens {
        rateLimits.limitIp("auth-id-token", ip, LIMIT_PER_IP)
        val token = verify(IdentityProvider.GOOGLE, idToken) { nonce == null || it == nonce }
        return signIn(IdentityProvider.GOOGLE, token, displayName = null, deviceName)
    }

    private fun verify(
        provider: IdentityProvider,
        rawToken: String,
        nonceMatches: (String?) -> Boolean,
    ): VerifiedIdToken {
        val token = verifier.verify(provider, rawToken) ?: throw IdTokenInvalidException()
        if (!nonceMatches(token.nonce)) {
            log.info("Rejected an ID token: provider={}, reason=nonce", provider)
            throw IdTokenInvalidException()
        }
        return token
    }

    private fun signIn(
        provider: IdentityProvider,
        token: VerifiedIdToken,
        displayName: String?,
        deviceName: String?,
    ): SessionTokens =
        transactions.execute {
            val now = clock.instant()
            val email = token.email?.trim()?.takeIf { it.isNotEmpty() && emailTrusted(provider, token, it) }
            val userId =
                users.findUserIdByIdentity(provider, token.subject)
                    ?: email?.let { users.findOrCreateByVerifiedEmail(it, now, displayName) }
                    ?: throw EmailNotVerifiedException()
            users.linkIdentity(userId, provider, token.subject, email, now)
            log.info("Signed in with an ID token: provider={}, userId={}", provider, userId)
            startSession.start(userId, deviceName, now)
        } ?: error("transaction returned no tokens")

    private fun emailTrusted(
        provider: IdentityProvider,
        token: VerifiedIdToken,
        email: String,
    ): Boolean {
        if (!token.emailVerified) return false
        if (provider != IdentityProvider.GOOGLE) return true
        val domain = email.substringAfterLast('@').lowercase()
        return domain == "gmail.com" || token.hostedDomain?.lowercase() == domain
    }

    companion object {
        const val LIMIT_PER_IP = 30
    }
}
