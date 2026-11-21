package com.onemind.core.presentation.format

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Bar nights cross midnight; Europe/Prague changes clocks at 01:00 UTC on 2026-03-29 and 2026-10-25. */
class TimeFormatTest {
    private val prague = TimeZone.of("Europe/Prague")

    private fun at(utc: String) = formatClockTime(Instant.parse(utc), prague)

    @Test
    fun ordinaryBarNightCrossesMidnight() {
        assertEquals("23:30", at("2026-06-12T21:30:00Z"))
        assertEquals("01:30", at("2026-06-12T23:30:00Z"))
        assertEquals("04:59", at("2026-06-13T02:59:00Z"))
    }

    @Test
    fun springForwardNightSkipsTwoOClock() {
        assertEquals("23:30", at("2026-03-28T22:30:00Z"))
        assertEquals("01:30", at("2026-03-29T00:30:00Z"))
        assertEquals("01:59", at("2026-03-29T00:59:00Z"))
        assertEquals("03:00", at("2026-03-29T01:00:00Z"))
        assertEquals("04:59", at("2026-03-29T02:59:00Z"))
    }

    @Test
    fun fallBackNightShowsTwoThirtyTwice() {
        assertEquals("23:30", at("2026-10-24T21:30:00Z"))
        assertEquals("01:30", at("2026-10-24T23:30:00Z"))
        assertEquals("02:30", at("2026-10-25T00:30:00Z"))
        assertEquals("02:30", at("2026-10-25T01:30:00Z"))
        assertEquals("04:59", at("2026-10-25T03:59:00Z"))
    }

    @Test
    fun usesTheVenueTimeZone() {
        val instant = Instant.parse("2026-06-12T21:30:00Z")
        assertEquals("22:30", formatClockTime(instant, TimeZone.of("Europe/London")))
        assertEquals("23:30", formatClockTime(instant, prague))
    }
}
