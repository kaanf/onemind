package com.onemind.sales.presentation.products

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.products.ProductsErrorCodes
import com.onemind.sales.domain.products.ProductsRepository
import com.onemind.sales.domain.products.ProductsSummary
import com.onemind.sales.presentation.FakeVenueScope
import com.onemind.sales.presentation.FakeVenues
import com.onemind.sales.presentation.VENUE_A
import com.onemind.sales.presentation.bothVenues
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_error_venue_not_found
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
class ProductsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class FakeRepository : ProductsRepository {
        val calls = mutableListOf<List<String>>()
        val results = ArrayDeque<Result<ProductsSummary, DataError>>()

        override suspend fun getProducts(venueIds: List<String>): Result<ProductsSummary, DataError> {
            calls += venueIds
            return results.removeFirstOrNull() ?: Result.Success(ProductsFixtures.ready)
        }
    }

    private fun viewModel(repository: ProductsRepository) =
        ProductsViewModel(repository, FakeVenueScope(VenueScope.Venue(VENUE_A)), FakeVenues(bothVenues))

    private suspend fun ReceiveTurbine<ProductsState>.awaitSettled(): ProductsState {
        while (true) {
            val item = awaitItem()
            if (item.content != ProductsContent.Loading && !item.isRefreshing) return item
        }
    }

    @Test
    fun theChartStartsOnTheBestSeller() =
        runTest(dispatcher) {
            val repository = FakeRepository()

            viewModel(repository).state.test {
                val state = awaitSettled()
                assertEquals("Pilsner", state.selectedProduct?.name)
                assertEquals(3, state.visibleProducts.size)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf(VENUE_A)), repository.calls)
        }

    @Test
    fun aChipFiltersWithoutAnotherCallAndResetsTheSelection() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                val first = awaitSettled()
                viewModel.onAction(ProductsAction.SelectProduct(first.visibleProducts[2].key))
                assertEquals("Water", awaitItem().selectedProduct?.name)
                viewModel.onAction(ProductsAction.SelectCategory("cocktails"))
                val filtered = awaitItem()
                assertEquals(listOf("Aperol Spritz"), filtered.visibleProducts.map { it.name })
                assertEquals("Aperol Spritz", filtered.selectedProduct?.name)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repository.calls.size)
        }

    @Test
    fun aMissingVenueSaysSo() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            repository.results += Result.Failure(DataError.Api(404, ProductsErrorCodes.VENUE_NOT_FOUND))

            viewModel(repository).state.test {
                val failed = assertIs<ProductsContent.Failed>(awaitSettled().content)
                assertEquals(UiText.Resource(Res.string.sales_detail_error_venue_not_found), failed.message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailedRefreshKeepsTheFigures() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val viewModel = viewModel(repository)

            viewModel.state.test {
                awaitSettled()
                repository.results += Result.Failure(DataError.Network.TIMEOUT)
                viewModel.onAction(ProductsAction.Refresh)
                val after = assertIs<ProductsContent.Ready>(awaitSettled().content)
                assertTrue(after.refreshError != null)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
