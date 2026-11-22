package com.onemind.today.presentation.today

import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.today.domain.HourKind
import com.onemind.today.domain.LossKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Sample figures for the `@Preview`s only (the canvas values); never used by the app. */
internal object TodayPreviewData {
    private val heights = listOf(18, 24, 31, 42, 55, 70, 82, 90, 84, 66, 40)

    val ready =
        TodayUi(
            businessDay = LocalDate(2026, 11, 6),
            clockTime = "21:40",
            updatedAt = "21:38",
            waitingForFirstSync = false,
            pendingVenueCount = 0,
            paidSoFar = "48 320 Kč",
            comparison = ComparisonUi(delta = "+12.4%", tone = BadgeTone.Positive, previousWeekday = DayOfWeek.FRIDAY),
            openTabsUnavailable = true,
            tips = "3 940 Kč",
            hourly =
                HourlyUi(
                    bars =
                        heights.mapIndexed { index, height ->
                            HourBarUi(
                                hour = (16 + index) % 24,
                                kind =
                                    when {
                                        index < 6 -> HourKind.PAST
                                        index == 6 -> HourKind.CURRENT
                                        else -> HourKind.FORECAST
                                    },
                                // The last forecast hour has no earlier weekday: an empty slot.
                                heightFraction = if (index == heights.lastIndex) null else height / 90f,
                            )
                        },
                    peakHour = 21,
                ),
            occupancyUnavailable = true,
            guests = "286",
            perGuest = "169 Kč",
            possibleLoss =
                PossibleLossUi(
                    total = "1 870 Kč",
                    includes = listOf(LossKind.DISCOUNTS, LossKind.REFUNDS, LossKind.DELETED_BILLS),
                ),
            dataGaps = emptyList(),
        )

    val waitingForFirstSync =
        ready.copy(
            updatedAt = null,
            waitingForFirstSync = true,
            paidSoFar = null,
            comparison = null,
            tips = null,
            hourly = null,
            guests = null,
            perGuest = null,
            possibleLoss = null,
        )

    val withGaps =
        ready.copy(
            pendingVenueCount = 1,
            dataGaps = listOf(DataGapLine(DataGapKind.UNREADABLE, 2), DataGapLine(DataGapKind.PAYMENT_MISMATCH, 1)),
        )
}
