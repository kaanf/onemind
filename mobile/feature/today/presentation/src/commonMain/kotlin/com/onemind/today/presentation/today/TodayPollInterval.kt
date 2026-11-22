package com.onemind.today.presentation.today

import com.onemind.today.domain.TodaySummary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long Today waits before the next poll (ADR 0005: 30 s while the venue is open, 5 min when it
 * is closed). The API doesn't say yet whether a venue is open, so it is 30 s for now; the open/closed
 * rule plugs in here once the answer carries it (roadmap: `refreshAfterSeconds` / open flag).
 */
fun interface TodayPollInterval {
    fun after(summary: TodaySummary?): Duration

    companion object {
        val Default = TodayPollInterval { 30.seconds }
    }
}
