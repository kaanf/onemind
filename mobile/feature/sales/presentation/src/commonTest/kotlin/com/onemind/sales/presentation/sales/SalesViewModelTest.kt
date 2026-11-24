package com.onemind.sales.presentation.sales

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.CachedSales
import com.onemind.sales.domain.SalesErrorCodes
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRepository
import com.onemind.sales.domain.SalesSummary
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_error_venues_differ
import com.onemind.sales.presentation.sales.SalesFixtures.VENUE_A
import com.onemind.sales.presentation.sales.SalesFixtures.VENUE_B
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {
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

        override suspend fun refresh(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private class FakeRepository : SalesRepository {
        val calls = mutableListOf<Pair<List<String>, SalesPeriod>>()
        val results = ArrayDeque<Result<SalesSummary, DataError>>()
        var fallback: Result<SalesSummary, DataError> = Result.Success(SalesFixtures.week)
        var cached: CachedSales? = null

        override suspend fun getSales(
            venueIds: List<String>,
            period: SalesPeriod,
        ): Result<SalesSummary, DataError> {
            calls += venueIds to period
            return results.removeFirstOrNull() ?: fallback
        }

        override suspend fun cachedSales(
            venueIds: List<String>,
            period: SalesPeriod,
        ) = cached
    }

    private val barA = Venue(VENUE_A, "Main bar", Currency.CZK, followed = true)
    private val barB = Venue(VENUE_B, "Garden", Currency.CZK, followed = false)
    private val bothVenues = VenueList.Loaded(listOf(barA, barB))

    private fun viewModel(
        repository: SalesRepository,
        scope: VenueScope? = VenueScope.Venue(VENUE_A),
        venues: VenueList = bothVenues,
    ) = SalesViewModel(repository, FakeVenueScope(scope), FakeVenues(venues), SalesPollInterval.Default)

    private suspend fun ReceiveTurbine<SalesState>.awaitReady(): SalesState {
        while (true) {
            val item = awaitItem()
            if (item.content is SalesContent.Ready) return item
        }
    }

    @Test
    fun loadsTheWeekOfTheSelectedVenueByDefault() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                val state = awaitReady()
                assertEquals(SalesPeriod.WEEK, state.period)
                assertEquals("Main bar", state.venueName)
                assertEquals(SalesFixtures.week.toUi(), (state.content as SalesContent.Ready).sales)
                assertEquals(listOf(listOf(VENUE_A) to SalesPeriod.WEEK), repository.calls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun allVenuesAsksForTheFollowedOnes() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = VenueScope.All).state.test {
                val state = awaitReady()
                assertTrue(state.isAllVenues)
                assertEquals(listOf(VENUE_A), repository.calls.single().first)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anAccountWithoutVenuesHasNothingToLoad() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = null, venues = VenueList.Loaded(emptyList())).state.test {
                assertEquals(SalesContent.Loading, awaitItem().content)
                assertEquals(SalesContent.NoVenue, awaitItem().content)
                assertTrue(repository.calls.isEmpty())
            }
        }

    @Test
    fun choosingAPeriodLoadsIt() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val vm = viewModel(repository)

            vm.state.test {
                awaitReady()
                vm.onAction(SalesAction.SelectPeriod(SalesPeriod.MONTH))
                while (awaitItem().period != SalesPeriod.MONTH) Unit
                awaitReady()
                assertEquals(listOf(SalesPeriod.WEEK, SalesPeriod.MONTH), repository.calls.map { it.second })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun pollsAgainAfterTheInterval() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                awaitReady()
                advanceTimeBy(60.seconds)
                runCurrent()
                assertEquals(2, repository.calls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedFirstLoadShowsTheMessage() =
        runTest(dispatcher) {
            val repository = FakeRepository().apply { fallback = Result.Failure(DataError.Api(422, SalesErrorCodes.CURRENCIES_DIFFER)) }

            viewModel(repository).state.test {
                var content = awaitItem().content
                while (content !is SalesContent.Failed) content = awaitItem().content
                assertEquals(UiText.Resource(Res.string.sales_error_venues_differ), content.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedPollKeepsTheFiguresWithANote() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Success(SalesFixtures.week)
            repository.results += Result.Failure(DataError.Api(422, SalesErrorCodes.BUSINESS_DAYS_DIFFER))

            viewModel(repository).state.test {
                awaitReady()
                advanceTimeBy(60.seconds)
                runCurrent()
                val ready = assertIs<SalesContent.Ready>(expectMostRecentItem().content)
                assertEquals(SalesFixtures.week.toUi(), ready.sales)
                assertEquals(UiText.Resource(Res.string.sales_error_venues_differ), ready.refreshError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun cachedFiguresShowWithTheirAgeAndOfflineAfterANetworkFailure() =
        runTest(dispatcher) {
            val repository =
                FakeRepository().apply {
                    cached = CachedSales(SalesFixtures.week, Instant.parse("2026-11-06T20:40:00Z"))
                    fallback = Result.Failure(DataError.Network.NO_INTERNET)
                }

            viewModel(repository).state.test {
                val first = assertIs<SalesContent.Ready>(awaitReady().content)
                assertEquals(SalesCachedMarker(time = "21:40", offline = false), first.cached)
                val second = assertIs<SalesContent.Ready>(awaitReady().content)
                assertEquals(SalesCachedMarker(time = "21:40", offline = true), second.cached)
                assertNull(second.refreshError)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
