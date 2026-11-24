package com.onemind.sales.presentation.losses

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.losses.LossesErrorCodes
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesRepository
import com.onemind.sales.domain.losses.LossesSummary
import com.onemind.sales.presentation.FakeVenueScope
import com.onemind.sales.presentation.FakeVenues
import com.onemind.sales.presentation.VENUE_A
import com.onemind.sales.presentation.bothVenues
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_error_venues_differ
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LossesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakeRepository : LossesRepository {
        val calls = mutableListOf<Pair<List<String>, LossesPeriod>>()
        val results = ArrayDeque<Result<LossesSummary, DataError>>()

        override suspend fun getLosses(
            venueIds: List<String>,
            period: LossesPeriod,
        ): Result<LossesSummary, DataError> {
            calls += venueIds to period
            return results.removeFirstOrNull() ?: Result.Success(LossesFixtures.ready.copy(period = period))
        }
    }

    private fun viewModel(
        repository: LossesRepository,
        scope: VenueScope? = VenueScope.Venue(VENUE_A),
        venues: FakeVenues = FakeVenues(bothVenues),
    ) = LossesViewModel(repository, FakeVenueScope(scope), venues)

    private suspend fun ReceiveTurbine<LossesState>.awaitSettled(): LossesState {
        while (true) {
            val item = awaitItem()
            if (item.content != LossesContent.Loading && !item.isRefreshing) return item
        }
    }

    @Test
    fun loadsTheWeekOfTheSelectedVenue() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                val state = awaitSettled()
                assertEquals(LossesPeriod.WEEK, state.period)
                assertIs<LossesContent.Ready>(state.content)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE_A) to LossesPeriod.WEEK), repository.calls)
        }

    @Test
    fun anAccountWithoutVenuesHasNothingToLoad() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, scope = null, venues = FakeVenues(VenueList.Loaded(emptyList()))).state.test {
                assertEquals(LossesContent.NoVenue, awaitSettled().content)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun choosingAPeriodLoadsIt() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                awaitSettled()
                viewModel.onAction(LossesAction.SelectPeriod(LossesPeriod.MONTH))
                val state = awaitSettled()
                assertEquals(LossesPeriod.MONTH, state.period)
                assertEquals(LossesPeriod.MONTH, assertIs<LossesContent.Ready>(state.content).losses.period)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(LossesPeriod.WEEK, LossesPeriod.MONTH), repository.calls.map { it.second })
        }

    @Test
    fun venuesThatCantBeShownTogetherSaySo() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Failure(DataError.Api(422, LossesErrorCodes.CURRENCIES_DIFFER))

            viewModel(repository, scope = VenueScope.All).state.test {
                val failed = assertIs<LossesContent.Failed>(awaitSettled().content)
                assertEquals(UiText.Resource(Res.string.sales_detail_error_venues_differ), failed.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedRefreshKeepsTheFigures() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                val first = assertIs<LossesContent.Ready>(awaitSettled().content)
                repository.results += Result.Failure(DataError.Network.NO_INTERNET)
                viewModel.onAction(LossesAction.Refresh)
                val after = assertIs<LossesContent.Ready>(awaitSettled().content)
                assertEquals(first.losses, after.losses)
                assertTrue(after.refreshError != null)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, repository.calls.size)
        }
}
