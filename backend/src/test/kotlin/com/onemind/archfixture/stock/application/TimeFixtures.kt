package com.onemind.archfixture.stock.application

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Rule 6 violations: each reads the system clock or zone instead of the injected Clock.
class UsesInstantNow {
    fun now(): Instant = Instant.now()
}

class UsesNowInZone {
    fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Prague"))
}

class UsesSystemZone {
    fun zone(): ZoneId = ZoneId.systemDefault()
}

class UsesCurrentTimeMillis {
    fun millis(): Long = System.currentTimeMillis()
}

class UsesSystemClock {
    fun clock(): Clock = Clock.systemUTC()
}

// Allowed: the injected Clock, the now(Clock) overloads, and nanoTime for durations.
class UsesInjectedClock(
    private val clock: Clock,
) {
    fun now(): Instant = Instant.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    fun instant(): Instant = clock.instant()

    fun elapsed(): Long = System.nanoTime()
}
