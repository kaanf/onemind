package com.onemind.today.presentation.today

import com.onemind.core.designsystem.components.BadgeTone
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.today.domain.DataGaps
import com.onemind.today.domain.DataStatus
import com.onemind.today.domain.HourKind
import com.onemind.today.domain.LossKind
import com.onemind.today.domain.TodayVenue
import com.onemind.today.presentation.TodayFixtures
import com.onemind.today.presentation.TodayFixtures.VENUE_A
import com.onemind.today.presentation.TodayFixtures.VENUE_B
import com.onemind.today.presentation.TodayFixtures.prague
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TodayUiTest {
    private val now = Instant.parse("2026-11-06T20:40:00Z")

    @Test
    fun readyFiguresAreFormattedInTheVenueLocale() {
        val ui = TodayFixtures.ready.toUi(now)

        assertEquals(TodayFixtures.shown("48·320_Kč"), ui.paidSoFar)
        assertEquals(TodayFixtures.shown("3·940_Kč"), ui.tips)
        assertEquals("286", ui.guests)
        assertEquals(TodayFixtures.shown("169_Kč"), ui.perGuest)
        assertEquals(TodayFixtures.shown("1·870_Kč"), ui.possibleLoss?.total)
        assertEquals(listOf(LossKind.DISCOUNTS, LossKind.REFUNDS), ui.possibleLoss?.includes)
        assertEquals(false, ui.waitingForFirstSync)
    }

    @Test
    fun theHeaderShowsTheBusinessDayAndTheVenueClock() {
        val ui = TodayFixtures.ready.toUi(now)

        assertEquals(LocalDate(2026, 11, 6), ui.businessDay)
        assertEquals("21:40", ui.clockTime)
        assertEquals("21:38", ui.updatedAt)
    }

    @Test
    fun afterMidnightTheDateIsStillTheEveningsBusinessDay() {
        // 01:15 on Saturday in Prague, still Friday's business day (rule 6): the date comes from the API.
        val ui = TodayFixtures.ready.toUi(Instant.parse("2026-11-07T00:15:00Z"))

        assertEquals(LocalDate(2026, 11, 6), ui.businessDay)
        assertEquals("01:15", ui.clockTime)
    }

    @Test
    fun noDataYetHasNoFiguresAndNoZeros() {
        val ui = TodayFixtures.noDataYet.toUi(now)

        assertTrue(ui.waitingForFirstSync)
        assertNull(ui.paidSoFar)
        assertNull(ui.tips)
        assertNull(ui.guests)
        assertNull(ui.perGuest)
        assertNull(ui.possibleLoss)
        assertNull(ui.hourly)
        assertNull(ui.comparison)
        assertNull(ui.updatedAt)
        // The whole screen says it is waiting; the per-venue note is for venues next to ones with data.
        assertEquals(0, ui.pendingVenueCount)
    }

    @Test
    fun venuesStillSyncingNextToReadyOnesAreCounted() {
        val summary = TodayFixtures.ready.copy(pendingVenueIds = listOf(VENUE_B))

        assertEquals(1, summary.toUi(now).pendingVenueCount)
    }

    @Test
    fun updatedAtIsTheOldestSyncOfTheVenuesWithData() {
        val summary =
            TodayFixtures.ready.copy(
                venues =
                    listOf(
                        TodayVenue(VENUE_A, LocalDate(2026, 11, 6), prague, DataStatus.READY, Instant.parse("2026-11-06T20:38:00Z")),
                        TodayVenue(VENUE_B, LocalDate(2026, 11, 6), prague, DataStatus.READY, Instant.parse("2026-11-06T20:31:00Z")),
                    ),
            )

        assertEquals("21:31", summary.toUi(now).updatedAt)
    }

    @Test
    fun theComparisonPillCarriesTheDeltaAndThePreviousWeekday() {
        val comparison = TodayFixtures.ready.toUi(now).comparison!!

        assertEquals("+12.4%", comparison.delta)
        assertEquals(BadgeTone.Positive, comparison.tone)
        assertEquals(DayOfWeek.FRIDAY, comparison.previousWeekday)
    }

    @Test
    fun theComparisonPillIsHiddenWithoutADelta() {
        assertNull(
            TodayFixtures.ready
                .copy(comparison = null)
                .toUi(now)
                .comparison,
        )
        val previousZero = TodayFixtures.ready.comparison!!.copy(deltaPercent = null)
        assertNull(
            TodayFixtures.ready
                .copy(comparison = previousZero)
                .toUi(now)
                .comparison,
        )
    }

    @Test
    fun theDeltaToneFollowsTheSign() {
        fun tone(delta: String) =
            TodayFixtures.ready
                .copy(comparison = TodayFixtures.ready.comparison!!.copy(deltaPercent = DecimalValue.parse(delta)))
                .toUi(now)
                .comparison!!
                .tone

        assertEquals(BadgeTone.Negative, tone("-3.0"))
        assertEquals(BadgeTone.Neutral, tone("0.0"))
    }

    @Test
    fun unavailableFiguresDecideTheEmptyStateCards() {
        val ui = TodayFixtures.ready.toUi(now)
        assertTrue(ui.openTabsUnavailable)
        assertTrue(ui.occupancyUnavailable)

        val none = TodayFixtures.ready.copy(unavailable = emptySet()).toUi(now)
        assertEquals(false, none.openTabsUnavailable)
        assertEquals(false, none.occupancyUnavailable)
    }

    @Test
    fun hourlyBarsScaleToTheLargestAndAForecastWithoutHistoryIsEmpty() {
        val hourly = TodayFixtures.ready.toUi(now).hourly!!

        assertEquals(listOf(0.5f, 4_200f / 18_200f, 1f, 0f), hourly.bars.map { it.heightFraction })
        assertEquals(listOf(HourKind.PAST, HourKind.CURRENT, HourKind.FORECAST, HourKind.FORECAST), hourly.bars.map { it.kind })
        // The spoken peak is of revenue paid so far, never a forecast.
        assertEquals(20, hourly.peakHour)
    }

    @Test
    fun anHourlyChartWithoutSalesHasFlatBarsAndNoPeak() {
        val empty =
            TodayFixtures.ready.hourly!!
                .hours
                .map { it.copy(amount = TodayFixtures.czk(0)) }
        val hourly =
            TodayFixtures.ready
                .copy(hourly = TodayFixtures.ready.hourly!!.copy(hours = empty))
                .toUi(now)
                .hourly!!

        assertTrue(hourly.bars.all { it.heightFraction == 0f })
        assertNull(hourly.peakHour)
    }

    @Test
    fun dataGapsListOnlyTheCountsAboveZero() {
        assertTrue(
            TodayFixtures.ready
                .toUi(now)
                .dataGaps
                .isEmpty(),
        )

        val ui = TodayFixtures.ready.copy(dataGaps = DataGaps(2, 0, 0, 1)).toUi(now)

        assertEquals(
            listOf(DataGapLine(DataGapKind.UNREADABLE, 2), DataGapLine(DataGapKind.PAYMENT_MISMATCH, 1)),
            ui.dataGaps,
        )
    }
}
