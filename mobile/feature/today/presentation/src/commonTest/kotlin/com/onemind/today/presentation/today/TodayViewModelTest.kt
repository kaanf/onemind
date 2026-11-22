package com.onemind.today.presentation.today

import app.cash.turbine.test
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.today.domain.TodayErrorCodes
import com.onemind.today.domain.TodayRepository
import com.onemind.today.domain.TodaySummary
import com.onemind.today.presentation.TodayFixtures
import com.onemind.today.presentation.TodayFixtures.VENUE_A
import com.onemind.today.presentation.generated.resources.Res
import com.onemind.today.presentation.generated.resources.today_error_venues_differ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakeVenueScope(
        initial: VenueScope?,
    ) : VenueScopeRepository {
        private val state = MutableStateFlow(initial)
        override val selected: StateFlow<VenueScope?> = state

        override fun select(scope: VenueScope) {
            state.value = scope
        }
    }

    private class FakeRepository : TodayRepository {
        val calls = mutableListOf<List<String>>()
        val results = ArrayDeque<Result<TodaySummary, DataError>>()
        var fallback: Result<TodaySummary, DataError> = Result.Success(TodayFixtures.ready)

        override suspend fun getToday(venueIds: List<String>): Result<TodaySummary, DataError> {
            calls += venueIds
            return results.removeFirstOrNull() ?: fallback
        }
    }

    /** Virtual time from 21:40:00 Prague on the fixture's Friday. */
    private fun TestScope.clock() =
        object : Clock {
            override fun now() = Instant.parse("2026-11-06T20:40:00Z") + testScheduler.currentTime.milliseconds
        }

    private fun TestScope.viewModel(
        repository: TodayRepository,
        scope: VenueScope? = VenueScope.Venue(VENUE_A),
    ) = TodayViewModel(repository, FakeVenueScope(scope), clock(), TodayPollInterval.Default)

    @Test
    fun withoutAVenueThereIsNothingToLoad() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = null).state.test {
                assertEquals(TodayContent.Loading, awaitItem().content)
                assertEquals(TodayContent.NoVenue, awaitItem().content)
            }
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun allVenuesWaitsForTheVenueList() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = VenueScope.All).state.test {
                skipItems(1)
                assertEquals(TodayContent.NoVenue, awaitItem().content)
            }
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun loadsTheSelectedVenue() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                assertEquals(TodayContent.Loading, awaitItem().content)
                val ready = assertIs<TodayContent.Ready>(awaitItem().content)
                assertEquals(TodayFixtures.shown("48·320_Kč"), ready.today.paidSoFar)
                assertEquals("21:40", ready.today.clockTime)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE_A)), repository.calls)
        }

    @Test
    fun pollsEvery30SecondsWhileCollected() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                runCurrent()
                assertEquals(1, repository.calls.size)
                advanceTimeBy(29.seconds)
                runCurrent()
                assertEquals(1, repository.calls.size)
                advanceTimeBy(1.seconds)
                runCurrent()
                assertEquals(2, repository.calls.size)
                advanceTimeBy(60.seconds)
                runCurrent()
                assertEquals(4, repository.calls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun stopsPollingSoonAfterTheScreenStopsCollecting() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(10.minutes())
            runCurrent()

            assertEquals(1, repository.calls.size)
        }

    @Test
    fun theClockMovesEachMinuteBetweenPolls() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = TodayViewModel(repository, FakeVenueScope(VenueScope.Venue(VENUE_A)), clock()) { 5.minutes() }

            viewModel.state.test {
                skipItems(1)
                assertEquals("21:40", assertIs<TodayContent.Ready>(awaitItem().content).today.clockTime)
                advanceTimeBy(60.seconds)
                assertEquals("21:41", assertIs<TodayContent.Ready>(awaitItem().content).today.clockTime)
                assertEquals(1, repository.calls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedFirstLoadShowsTheErrorAndRefreshRetries() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Failure(DataError.Network.NO_INTERNET)
            val viewModel = viewModel(repository)

            viewModel.state.test {
                skipItems(1)
                assertIs<TodayContent.Failed>(awaitItem().content)

                viewModel.onAction(TodayAction.Refresh)
                assertTrue(awaitItem().isRefreshing)
                val after = awaitItem()
                assertIs<TodayContent.Ready>(after.content)
                assertEquals(false, after.isRefreshing)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, repository.calls.size)
        }

    @Test
    fun aFailedPollKeepsTheLastFiguresWithANote() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Success(TodayFixtures.ready)
            repository.results += Result.Failure(DataError.Network.TIMEOUT)
            val viewModel = viewModel(repository)

            viewModel.state.test {
                skipItems(1)
                assertIs<TodayContent.Ready>(awaitItem().content)
                advanceTimeBy(30.seconds)
                val stale = assertIs<TodayContent.Ready>(awaitItem().content)
                assertEquals(TodayFixtures.shown("48·320_Kč"), stale.today.paidSoFar)
                assertIs<UiText>(stale.refreshError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun venuesThatCantBeCombinedGetTheirOwnMessage() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.fallback = Result.Failure(DataError.Api(422, TodayErrorCodes.BUSINESS_DAYS_DIFFER))

            viewModel(repository).state.test {
                skipItems(1)
                val failed = assertIs<TodayContent.Failed>(awaitItem().content)
                assertEquals(UiText.Resource(Res.string.today_error_venues_differ), failed.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noDataYetIsShownAsWaitingNotAsAnError() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.fallback = Result.Success(TodayFixtures.noDataYet)

            viewModel(repository).state.test {
                skipItems(1)
                assertTrue(assertIs<TodayContent.Ready>(awaitItem().content).today.waitingForFirstSync)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun Int.minutes() = (this * 60).seconds
}
