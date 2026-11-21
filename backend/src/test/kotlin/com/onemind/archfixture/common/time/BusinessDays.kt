package com.onemind.archfixture.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Rule 6: common.time is where instants become local dates.
class BusinessDays {
    fun date(
        at: Instant,
        zone: ZoneId,
    ): LocalDate = at.atZone(zone).toLocalDate()
}
