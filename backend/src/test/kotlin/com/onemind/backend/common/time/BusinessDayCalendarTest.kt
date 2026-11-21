package com.onemind.backend.common.time

import com.onemind.backend.common.time.BusinessDayCalendar.DEFAULT_BUSINESS_DAY_START
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BusinessDayCalendarTest {
    private val prague = ZoneId.of("Europe/Prague")
    private val start = DEFAULT_BUSINESS_DAY_START

    private fun at(local: String) = LocalDateTime.parse(local).atZone(prague).toInstant()

    private fun dateOf(local: String) = BusinessDayCalendar.businessDate(at(local), prague, start)

    @Test
    fun `a bar night belongs to the day it started until the cut-off`() {
        val friday = LocalDate.parse("2026-11-20")
        assertEquals(friday, dateOf("2026-11-20T23:30"))
        assertEquals(friday, dateOf("2026-11-21T01:30"))
        assertEquals(friday, dateOf("2026-11-21T04:59:59.999"))
        assertEquals(friday.plusDays(1), dateOf("2026-11-21T05:00"))
    }

    @Test
    fun `the cut-off is a parameter`() {
        val sixteen = LocalTime.of(16, 0)
        val instant = at("2026-11-21T15:59")
        assertEquals(LocalDate.parse("2026-11-20"), BusinessDayCalendar.businessDate(instant, prague, sixteen))
        assertEquals(LocalDate.parse("2026-11-21"), BusinessDayCalendar.businessDate(instant, prague, start))
    }

    @Test
    fun `a normal day lasts 24 hours`() {
        val range = BusinessDayCalendar.range(LocalDate.parse("2026-11-20"), prague, start)
        assertEquals(at("2026-11-20T05:00"), range.start)
        assertEquals(at("2026-11-21T05:00"), range.end)
        assertEquals(Duration.ofHours(24), range.length)
    }

    @Test
    fun `the spring-forward night gives a 23 hour business day`() {
        // Europe/Prague 2026-03-29: 02:00 CET jumps to 03:00 CEST.
        val range = BusinessDayCalendar.range(LocalDate.parse("2026-03-28"), prague, start)
        assertEquals(Duration.ofHours(23), range.length)
        assertEquals(LocalDate.parse("2026-03-28"), dateOf("2026-03-29T03:30"))
        assertEquals(LocalDate.parse("2026-03-29"), dateOf("2026-03-29T05:00"))
    }

    @Test
    fun `the fall-back night gives a 25 hour business day and both 02_30s belong to it`() {
        // Europe/Prague 2026-10-25: 03:00 CEST falls back to 02:00 CET, so 02:xx happens twice.
        val day = LocalDate.parse("2026-10-24")
        val range = BusinessDayCalendar.range(day, prague, start)
        assertEquals(Duration.ofHours(25), range.length)

        val first = OffsetDateTime.of(2026, 10, 25, 2, 30, 0, 0, ZoneOffset.ofHours(2)).toInstant()
        val second = OffsetDateTime.of(2026, 10, 25, 2, 30, 0, 0, ZoneOffset.ofHours(1)).toInstant()
        assertEquals(Duration.ofHours(1), Duration.between(first, second))
        listOf(first, second).forEach {
            assertEquals(day, BusinessDayCalendar.businessDate(it, prague, start))
            assertTrue(it in range)
        }
        assertEquals(day.plusDays(1), dateOf("2026-10-25T05:00"))
    }

    @Test
    fun `a cut-off inside the DST gap shifts forward by the gap`() {
        val range = BusinessDayCalendar.range(LocalDate.parse("2026-03-29"), prague, LocalTime.of(2, 30))
        assertEquals(at("2026-03-29T03:30"), range.start)
        assertEquals(Duration.ofHours(23), range.length)
    }

    @Test
    fun `range end is exclusive`() {
        val range = BusinessDayCalendar.range(LocalDate.parse("2026-11-20"), prague, start)
        assertTrue(range.start in range)
        assertFalse(range.end in range)
    }

    @Test
    fun `today reads the injected clock`() {
        val clock = Clock.fixed(at("2026-11-21T02:00"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-11-20"), BusinessDayCalendar.today(clock, prague, start))
    }
}
