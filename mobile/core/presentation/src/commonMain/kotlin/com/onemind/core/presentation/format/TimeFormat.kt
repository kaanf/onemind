package com.onemind.core.presentation.format

import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val clockTime =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }

/** Wall-clock time (*23:30*) of [instant] in the venue's time zone from the API, never the device's. */
fun formatClockTime(
    instant: Instant,
    venueTimeZone: TimeZone,
): String = instant.toLocalDateTime(venueTimeZone).time.format(clockTime)
