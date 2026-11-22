package com.onemind.today.presentation.today

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.profile.Profile
import com.onemind.core.domain.profile.ProfileRepository
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.today.domain.CachedToday
import com.onemind.today.domain.TodayErrorCodes
import com.onemind.today.domain.TodayRepository
import com.onemind.today.domain.TodaySummary
import com.onemind.today.presentation.TodayFixtures
import com.onemind.today.presentation.TodayFixtures.VENUE_A
import com.onemind.today.presentation.TodayFixtures.VENUE_B
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
import kotlin.test.assertNull
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

        override fun clear() {
            state.value = null
        }
    }

    private class FakeVenues(
        initial: VenueList,
    ) : VenueRepository {
        val state = MutableStateFlow(initial)
        override val venues: StateFlow<VenueList> = state
        var refreshes = 0
        var onRefresh: () -> VenueList = { state.value }

        override suspend fun refresh(): EmptyResult<DataError> {
            refreshes++
            state.value = onRefresh()
            return Result.Success(Unit)
        }
    }

    private class FakeProfile(
        initial: Profile? = null,
    ) : ProfileRepository {
        override val profile = MutableStateFlow(initial)

        override suspend fun refresh() = Unit
    }

    private val barA = Venue(VENUE_A, "Main bar", Currency.CZK, followed = true)
    private val barB = Venue(VENUE_B, "Garden", Currency.CZK, followed = false)
    private val bothVenues = VenueList.Loaded(listOf(barA, barB))

    private class FakeRepository : TodayRepository {
        val calls = mutableListOf<List<String>>()
        val results = ArrayDeque<Result<TodaySummary, DataError>>()
        var fallback: Result<TodaySummary, DataError> = Result.Success(TodayFixtures.ready)

        var cached: CachedToday? = null

        override suspend fun getToday(venueIds: List<String>): Result<TodaySummary, DataError> {
            calls += venueIds
            return results.removeFirstOrNull() ?: fallback
        }

        override suspend fun cachedToday(venueIds: List<String>) = cached
    }

    /** Virtual time from 21:40:00 Prague on the fixture's Friday. */
    private fun TestScope.clock() =
        object : Clock {
            override fun now() = Instant.parse("2026-11-06T20:40:00Z") + testScheduler.currentTime.milliseconds
        }

    private fun TestScope.viewModel(
        repository: TodayRepository,
        scope: VenueScope? = VenueScope.Venue(VENUE_A),
        venues: VenueRepository = FakeVenues(bothVenues),
        profile: ProfileRepository = FakeProfile(),
        pollInterval: TodayPollInterval = TodayPollInterval.Default,
    ) = TodayViewModel(repository, FakeVenueScope(scope), venues, profile, clock(), pollInterval)

    @Test
    fun anAccountWithoutVenuesHasNothingToLoad() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = null, venues = FakeVenues(VenueList.Loaded(emptyList()))).state.test {
                assertEquals(TodayContent.Loading, awaitItem().content)
                assertEquals(TodayContent.NoVenue, awaitItem().content)
            }
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun itWaitsForTheVenueListBeforeLoading() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val venues = FakeVenues(VenueList.Loading)

            viewModel(repository, venues = venues).state.test {
                assertEquals(TodayContent.Loading, awaitItem().content)
                runCurrent()
                assertTrue(repository.calls.isEmpty())

                venues.state.value = bothVenues
                assertIs<TodayContent.Ready>(awaitNonLoading().content)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE_A)), repository.calls)
        }

    @Test
    fun allVenuesLoadsTheFollowedVenues() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = VenueScope.All).state.test {
                assertIs<TodayContent.Ready>(awaitNonLoading().content)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE_A)), repository.calls)
        }

    @Test
    fun aFailedVenueListShowsTheErrorAndRefreshLoadsItAgain() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val venues = FakeVenues(VenueList.Failed(DataError.Network.NO_INTERNET))
            venues.onRefresh = { bothVenues }
            val viewModel = viewModel(repository, venues = venues)

            viewModel.state.test {
                assertIs<TodayContent.Failed>(awaitNonLoading().content)

                viewModel.onAction(TodayAction.Refresh)
                runCurrent()
                assertEquals(1, venues.refreshes)
                assertIs<TodayContent.Ready>(expectMostRecentItem().content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theHeaderNamesTheScopeAndShowsTheInitials() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, profile = FakeProfile(Profile("Kaan Fırat"))).state.test {
                runCurrent()
                val header = expectMostRecentItem().header
                assertEquals(ScopeName.Venue("Main bar"), header.scopeName)
                assertEquals("KF", header.initials)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel(repository, scope = VenueScope.All, profile = FakeProfile(Profile(null))).state.test {
                runCurrent()
                val header = expectMostRecentItem().header
                assertEquals(ScopeName.AllVenues, header.scopeName)
                // No name given: no invented initials.
                assertNull(header.initials)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun loadsTheSelectedVenue() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                assertEquals(TodayContent.Loading, awaitItem().content)
                val ready = assertIs<TodayContent.Ready>(awaitNonLoading().content)
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
            val viewModel = viewModel(repository, pollInterval = { 5.minutes() })

            viewModel.state.test {
                assertEquals("21:40", assertIs<TodayContent.Ready>(awaitNonLoading().content).today.clockTime)
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
                assertIs<TodayContent.Failed>(awaitNonLoading().content)

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
                assertIs<TodayContent.Ready>(awaitNonLoading().content)
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
                val failed = assertIs<TodayContent.Failed>(awaitNonLoading().content)
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
                assertTrue(assertIs<TodayContent.Ready>(awaitNonLoading().content).today.waitingForFirstSync)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun cachedFiguresShowAtOnceWithTheirAgeAndFreshOnesDropTheMarker() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            // Received at 21:12 Prague, shown at launch before the network answers.
            repository.cached = CachedToday(TodayFixtures.ready.copy(guests = 100), Instant.parse("2026-11-06T20:12:00Z"))

            viewModel(repository).state.test {
                val cached = assertIs<TodayContent.Ready>(awaitNonLoading().content)
                assertEquals(CachedMarker(savedAt = "21:12", offline = false), cached.cached)
                assertEquals("100", cached.today.guests)

                val fresh = assertIs<TodayContent.Ready>(awaitNonLoading().content)
                assertNull(fresh.cached)
                assertEquals("286", fresh.today.guests)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun offlineTheCachedFiguresStayMarkedOffline() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.cached = CachedToday(TodayFixtures.ready, Instant.parse("2026-11-06T20:12:00Z"))
            repository.fallback = Result.Failure(DataError.Network.NO_INTERNET)

            viewModel(repository).state.test {
                skipItems(1)
                assertEquals(CachedMarker("21:12", offline = false), assertIs<TodayContent.Ready>(awaitNonLoading().content).cached)
                val offline = assertIs<TodayContent.Ready>(awaitNonLoading().content)
                assertEquals(CachedMarker("21:12", offline = true), offline.cached)
                // The offline marker says it; no second error line.
                assertNull(offline.refreshError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aServerErrorWithCachedFiguresKeepsTheAgeAndShowsTheError() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.cached = CachedToday(TodayFixtures.ready, Instant.parse("2026-11-06T20:12:00Z"))
            repository.fallback = Result.Failure(DataError.Api(422, TodayErrorCodes.BUSINESS_DAYS_DIFFER))

            viewModel(repository).state.test {
                skipItems(1)
                awaitNonLoading()
                val failed = assertIs<TodayContent.Ready>(awaitNonLoading().content)
                assertEquals(CachedMarker("21:12", offline = false), failed.cached)
                assertEquals(UiText.Resource(Res.string.today_error_venues_differ), failed.refreshError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun Int.minutes() = (this * 60).seconds

    /** The next state past loading (the header can change while Today still loads). */
    private suspend fun ReceiveTurbine<TodayState>.awaitNonLoading(): TodayState {
        while (true) {
            val state = awaitItem()
            if (state.content != TodayContent.Loading) return state
        }
    }
}
