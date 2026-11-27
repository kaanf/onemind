package com.onemind.staff.presentation.staff

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.Venue
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.staff.domain.StaffDataGaps
import com.onemind.staff.domain.StaffErrorCodes
import com.onemind.staff.domain.StaffMember
import com.onemind.staff.domain.StaffRepository
import com.onemind.staff.domain.StaffSummary
import com.onemind.staff.domain.StaffTotals
import com.onemind.staff.presentation.generated.resources.Res
import com.onemind.staff.presentation.generated.resources.staff_error_venue_not_found
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakeRepository : StaffRepository {
        val calls = mutableListOf<List<String>>()
        val results = ArrayDeque<Result<StaffSummary, DataError>>()

        override suspend fun getStaff(venueIds: List<String>): Result<StaffSummary, DataError> {
            calls += venueIds
            return results.removeFirstOrNull() ?: Result.Success(ready)
        }
    }

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
        override val venues: StateFlow<VenueList> = MutableStateFlow(initial)

        override suspend fun refresh(): EmptyResult<DataError> = Result.Success(Unit)
    }

    private fun viewModel(
        repository: StaffRepository,
        venues: VenueList = VenueList.Loaded(listOf(bar)),
    ) = StaffViewModel(repository, FakeVenueScope(VenueScope.Venue(VENUE)), FakeVenues(venues), pollInterval = 60.seconds)

    private suspend fun ReceiveTurbine<StaffState>.awaitSettled(): StaffState {
        while (true) {
            val item = awaitItem()
            if (item.content != StaffContent.Loading && !item.isRefreshing) return item
        }
    }

    @Test
    fun theDaysStaffAreShownLargestSalesFirst() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                val content = assertIs<StaffContent.Ready>(awaitSettled().content)
                assertEquals(listOf("Tomáš Novák", null), content.staff.staff.map { it.name })
                assertEquals(
                    "TN",
                    content.staff.staff
                        .first()
                        .initials,
                )
                assertEquals(
                    "?",
                    content.staff.staff
                        .last()
                        .initials,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE)), repository.calls)
        }

    @Test
    fun itPollsEveryIntervalAndKeepsFiguresWhenAPollFails() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                awaitSettled()
                repository.results += Result.Failure(DataError.Network.NO_INTERNET)
                advanceTimeBy(61.seconds)
                val content = assertIs<StaffContent.Ready>(awaitSettled().content)
                assertIs<UiText>(content.refreshError)
                assertEquals(2, content.staff.staff.size)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, repository.calls.size)
        }

    @Test
    fun anUnknownVenueIsSaidSo() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Failure(DataError.Api(404, StaffErrorCodes.VENUE_NOT_FOUND))

            viewModel(repository).state.test {
                val content = assertIs<StaffContent.Failed>(awaitSettled().content)
                assertEquals(UiText.Resource(Res.string.staff_error_venue_not_found), content.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun withoutVenuesThereIsNothingToLoad() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository, VenueList.Loaded(emptyList())).state.test {
                assertEquals(StaffContent.NoVenue, awaitSettled().content)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(emptyList(), repository.calls)
        }

    @Test
    fun initialsUseTheFirstAndLastWord() {
        assertEquals("TN", initials("Tomáš  Novák"))
        assertEquals("AP", initials("anna maria pokorná"))
        assertEquals("L", initials("lucie"))
        assertEquals("?", initials(" "))
        assertEquals("?", initials(null))
    }

    private companion object {
        const val VENUE = "0193a1b2-0000-7000-8000-00000000000a"
        val bar = Venue(VENUE, "Main bar", Currency.CZK, followed = true)

        fun czk(minor: Long) = Money(minor, Currency.CZK)

        val ready =
            StaffSummary(
                hasData = true,
                currency = Currency.CZK,
                businessDay = LocalDate(2026, 11, 20),
                pendingVenueCount = 0,
                totals = StaffTotals(czk(1_692_000), czk(121_000), 94, 35),
                staff =
                    listOf(
                        StaffMember("s1", "Tomáš Novák", czk(1_482_000), czk(121_000), 82, 31, 1),
                        StaffMember("s2", null, czk(210_000), czk(0), 12, 4, 0),
                    ),
                dataGaps = StaffDataGaps(0, 0, 0),
            )
    }
}
