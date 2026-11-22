package com.onemind.backend.pos.domain

import java.time.Instant
import java.time.LocalDate

/** How far the bills sync of a venue got (`sync_cursor` `BILLS`). */
sealed class BillSyncCursor {
    /**
     * The initial import is still running: [nextDay] is the first business day not imported yet. Once it
     * is done the sync continues from [startedAt], so nothing changed during the import is missed.
     */
    data class Initial(
        val nextDay: LocalDate,
        val startedAt: Instant,
    ) : BillSyncCursor()

    /** Incremental: bills modified at or after [since] (`modifiedSince`, inclusive). */
    data class Since(
        val since: Instant,
    ) : BillSyncCursor()

    fun format(): String =
        when (this) {
            is Initial -> "$INITIAL$SEPARATOR$nextDay$SEPARATOR$startedAt"
            is Since -> "$SINCE$SEPARATOR$since"
        }

    companion object {
        private const val INITIAL = "INITIAL"
        private const val SINCE = "SINCE"
        private const val SEPARATOR = "|"

        /** `null` when there is no cursor yet (nothing imported). */
        fun parse(value: String?): BillSyncCursor? {
            if (value == null) return null
            val parts = value.split(SEPARATOR)
            return when {
                parts.size == 3 && parts[0] == INITIAL -> Initial(LocalDate.parse(parts[1]), Instant.parse(parts[2]))
                parts.size == 2 && parts[0] == SINCE -> Since(Instant.parse(parts[1]))
                else -> throw IllegalArgumentException("Not a bills sync cursor: '$value'")
            }
        }
    }
}
