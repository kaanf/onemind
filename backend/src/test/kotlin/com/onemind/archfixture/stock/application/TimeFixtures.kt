package com.onemind.archfixture.stock.application

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.GregorianCalendar
import kotlin.time.ExperimentalTime

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

class UsesTickingSystemClock {
    fun clock(): Clock = Clock.tickSeconds(ZoneId.of("Europe/Prague"))
}

class UsesGregorianCalendar {
    fun calendar(): GregorianCalendar = GregorianCalendar()
}

@OptIn(ExperimentalTime::class)
class UsesKotlinSystemClock {
    fun clock(): kotlin.time.Clock = kotlin.time.Clock.System
}

// Rule 6 violations: business days from a calendar date instead of BusinessDayCalendar.
class BucketsByCalendarDate {
    fun day(
        closedAt: Instant,
        zone: ZoneId,
    ): LocalDate = closedAt.atZone(zone).toLocalDate()
}

class BucketsByOfInstant {
    fun day(
        closedAt: Instant,
        zone: ZoneId,
    ): LocalDate = LocalDate.ofInstant(closedAt, zone)
}

class BucketsByOffsetHour {
    fun hour(
        closedAt: Instant,
        offset: ZoneOffset,
    ): Int = closedAt.atOffset(offset).hour
}

// Allowed: an offset date-time for API mapping (no local date or hour taken).
class MapsToOffsetDateTime {
    fun toDto(at: Instant): OffsetDateTime = at.atOffset(ZoneOffset.UTC)
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
