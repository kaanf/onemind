package com.onemind.backend.common.ratelimit

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fixed-window request limits per key (for example `auth-email-start:ip:<address>`), in memory.
 * Enough for the single backend instance of ADR 0008; limits that must survive a restart or span
 * instances belong in the database.
 */
@Component
class RateLimiter(
    private val clock: Clock,
) {
    private data class Window(
        val start: Instant,
        val count: Int,
    )

    private val windows = ConcurrentHashMap<String, Window>()

    /** Counts one request for [key]; returns `null` if it is allowed, else how long until the window resets. */
    fun tryAcquire(
        key: String,
        limit: Int,
        window: Duration,
    ): Duration? {
        val now = clock.instant()
        if (windows.size > MAX_KEYS) windows.values.removeIf { it.start + window <= now }
        var allowed = false
        val current =
            windows.compute(key) { _, existing ->
                when {
                    existing == null || existing.start + window <= now -> Window(now, 1).also { allowed = true }
                    existing.count < limit -> existing.copy(count = existing.count + 1).also { allowed = true }
                    else -> existing
                }
            }
        return if (allowed || current == null) null else Duration.between(now, current.start + window)
    }

    private companion object {
        const val MAX_KEYS = 10_000
    }
}
