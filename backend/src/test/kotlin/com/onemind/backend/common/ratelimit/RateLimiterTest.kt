package com.onemind.backend.common.ratelimit

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RateLimiterTest {
    private var now = Instant.parse("2026-11-21T12:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId) = this

            override fun instant() = now
        }
    private val limiter = RateLimiter(clock)
    private val window = Duration.ofMinutes(10)

    @Test
    fun `allows the limit per window and key, then says when to retry`() {
        repeat(3) { assertNull(limiter.tryAcquire("a", 3, window)) }
        assertNull(limiter.tryAcquire("b", 3, window))

        now += Duration.ofMinutes(4)
        assertEquals(Duration.ofMinutes(6), limiter.tryAcquire("a", 3, window))

        now += Duration.ofMinutes(6)
        assertNull(limiter.tryAcquire("a", 3, window))
    }
}
