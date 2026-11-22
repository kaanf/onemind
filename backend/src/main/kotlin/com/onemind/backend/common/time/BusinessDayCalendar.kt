package com.onemind.backend.common.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The one place that turns an instant into a venue's business day (AGENTS.md rule 6,
 * `docs/domain/METRICS.md#business-day`). A business day starts at the venue's `businessDayStart` in
 * its time zone and ends just before the next one, so a bar night that crosses midnight is one day and
 * a DST day lasts 23 or 25 hours. Callers pass the venue's zone and cut-off; nothing else hard-codes
 * either. "Now" comes from the injected [Clock] ([ClockConfig]).
 */
object BusinessDayCalendar {
    /** The cut-off until the venue sets its own (R28); a bill belongs to its paid business day (owner decision 2026-11-22). */
    val DEFAULT_BUSINESS_DAY_START: LocalTime = LocalTime.of(5, 0)

    /** The business date an instant belongs to: the local date, minus one day before the cut-off. */
    fun businessDate(
        at: Instant,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): LocalDate {
        val local = at.atZone(zone)
        return if (local.isBefore(start(local.toLocalDate(), zone, businessDayStart))) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /** The current business date of a venue. */
    fun today(
        clock: Clock,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): LocalDate = businessDate(clock.instant(), zone, businessDayStart)

    /**
     * The local clock hour (0–23) of an instant, for hourly buckets. The repeated hour of the autumn DST
     * change is one bucket; the skipped spring hour has none.
     */
    fun localHour(
        at: Instant,
        zone: ZoneId,
    ): Int = at.atZone(zone).hour

    /**
     * The local clock hours of a business date in order, each once: 24 on a normal day, 23 on the spring
     * DST day (the skipped hour is missing), 24 on the autumn one (the repeated hour is one, as in the
     * hourly rollups).
     */
    fun localHours(
        date: LocalDate,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): List<Int> {
        val day = range(date, zone, businessDayStart)
        return generateSequence(day.start) { it.plus(Duration.ofHours(1)) }
            .takeWhile { it.isBefore(day.end) }
            .map { localHour(it, zone) }
            .distinct()
            .toList()
    }

    /**
     * The instant [days] days before [at] at the same local clock time in [zone] ("last Friday, up to the
     * same time"), so a DST change in between doesn't shift it by an hour.
     */
    fun sameLocalTimeDaysEarlier(
        at: Instant,
        zone: ZoneId,
        days: Long,
    ): Instant = at.atZone(zone).minusDays(days).toInstant()

    /** The instants a business date covers, `[start, end)`. */
    fun range(
        date: LocalDate,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): BusinessDayRange =
        BusinessDayRange(
            start = start(date, zone, businessDayStart).toInstant(),
            end = start(date.plusDays(1), zone, businessDayStart).toInstant(),
        )

    // A cut-off inside a DST gap moves forward by the gap; inside an overlap it takes the earlier offset.
    private fun start(
        date: LocalDate,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): ZonedDateTime = ZonedDateTime.of(date, businessDayStart, zone)
}

/** A business day as instants: [start] inclusive, [end] exclusive. */
data class BusinessDayRange(
    val start: Instant,
    val end: Instant,
) {
    val length: Duration get() = Duration.between(start, end)

    operator fun contains(at: Instant): Boolean = !at.isBefore(start) && at.isBefore(end)
}
