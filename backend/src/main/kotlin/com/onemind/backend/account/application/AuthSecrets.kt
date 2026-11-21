package com.onemind.backend.account.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates and hashes the secrets of sign-in (ADR 0004). Codes are HMAC-SHA256 with a server-side
 * pepper (`onemind.auth.email-code-pepper`): a 6-digit code has too few values for a plain hash to
 * protect it if the database leaks. Refresh tokens have 256 random bits, so SHA-256 is enough.
 */
@Component
class AuthSecrets(
    @Value("\${onemind.auth.email-code-pepper:}") pepper: String,
    environment: Environment,
) {
    private val random = SecureRandom()
    private val pepperKey: SecretKeySpec =
        SecretKeySpec(
            pepper.takeIf { it.isNotBlank() }?.toByteArray()
                ?: run {
                    check(!environment.acceptsProfiles(Profiles.of("prod"))) { "onemind.auth.email-code-pepper is required in prod" }
                    LoggerFactory.getLogger(javaClass).warn("No email code pepper configured: using a temporary one")
                    ByteArray(32).also(random::nextBytes)
                },
            HMAC,
        )

    fun newEmailCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    fun hashEmailCode(
        email: String,
        code: String,
    ): String {
        val mac = Mac.getInstance(HMAC).apply { init(pepperKey) }
        return HexFormat.of().formatHex(mac.doFinal("${email.lowercase()}:$code".toByteArray()))
    }

    fun emailCodeMatches(
        email: String,
        code: String,
        hash: String,
    ): Boolean = MessageDigest.isEqual(hashEmailCode(email, code).toByteArray(), hash.toByteArray())

    fun newRefreshToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun hashRefreshToken(token: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private companion object {
        const val HMAC = "HmacSHA256"
    }
}
