package com.onemind.backend.account.application

import com.onemind.backend.common.error.RateLimitedException
import com.onemind.backend.common.ratelimit.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.Duration

/**
 * Per-IP limits of the sign-in endpoints (ADR 0004), in memory (one instance, ADR 0008). A request
 * whose client address is unknown or unparseable is limited under one shared key instead of
 * skipping the limit (fail closed).
 */
@Component
class AuthRateLimits(
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Throws [RateLimitedException] once [ip] has used up [limit] calls of [action] in [WINDOW]. */
    fun limitIp(
        action: String,
        ip: InetAddress?,
        limit: Int,
    ) {
        val client =
            ip?.hostAddress ?: run {
                // Many of these point to a proxy misconfiguration that puts every client under one key.
                log.warn("Client address unknown, rate-limited under a shared key: action={}", action)
                UNKNOWN_IP
            }
        rateLimiter.tryAcquire("$action:ip:$client", limit, WINDOW)?.let { throw RateLimitedException(it) }
    }

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(10)
        const val UNKNOWN_IP = "unknown"
    }
}
