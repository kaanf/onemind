package com.onemind.sales.presentation.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.core.presentation.util.toUiText
import com.onemind.core.presentation.venue.VenueTarget
import com.onemind.core.presentation.venue.venueTarget
import com.onemind.sales.domain.products.ProductsErrorCodes
import com.onemind.sales.domain.products.ProductsRepository
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_detail_error_venue_not_found
import com.onemind.sales.presentation.generated.resources.sales_detail_error_venues_differ
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 02a Products by hour: loads the current business day's products for the selected scope, again on a
 * scope change and on pull-to-refresh (with `If-None-Match`, ADR 0005). Chips and the selected row
 * filter on the device (no extra call). Not a live screen: no polling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModel(
    private val repository: ProductsRepository,
    venueScope: VenueScopeRepository,
    private val venueRepository: VenueRepository,
) : ViewModel() {
    private val reloads = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)
    private val categoryId = MutableStateFlow<String?>(null)
    private val productKey = MutableStateFlow<String?>(null)
    private var latestTarget: VenueTarget = VenueTarget.Waiting

    private val target: Flow<VenueTarget> =
        combine(venueScope.selected, venueRepository.venues, ::venueTarget).distinctUntilChanged()

    // The last figures per venue set, kept on screen while a refresh fails.
    private var shown: Pair<List<String>, ProductsUi>? = null

    val state: StateFlow<ProductsState> =
        combine(
            combine(target, reloads) { target, _ -> target }
                .flatMapLatest { target ->
                    latestTarget = target
                    when (target) {
                        VenueTarget.Waiting -> flowOf(ProductsContent.Loading)
                        VenueTarget.NoVenue -> flowOf(ProductsContent.NoVenue)
                        is VenueTarget.VenuesFailed -> flowOf(ProductsContent.Failed(target.error.toProductsMessage()))
                        is VenueTarget.Venues -> load(target.ids)
                    }
                },
            refreshing,
            categoryId,
            productKey,
        ) { content, isRefreshing, category, product -> ProductsState(content, isRefreshing, category, product) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProductsState())

    fun onAction(action: ProductsAction) {
        when (action) {
            is ProductsAction.SelectCategory -> {
                categoryId.value = action.categoryId
                productKey.value = null
            }

            is ProductsAction.SelectProduct -> {
                productKey.value = action.key
            }

            ProductsAction.Refresh -> {
                if (!state.value.canRefresh) return
                refreshing.value = true
                if (latestTarget is VenueTarget.VenuesFailed) {
                    viewModelScope.launch {
                        venueRepository.refresh()
                        refreshing.value = false
                    }
                } else {
                    reloads.value++
                }
            }
        }
    }

    private fun load(venueIds: List<String>): Flow<ProductsContent> =
        flow {
            val previous = shown?.takeIf { it.first == venueIds }?.second
            if (previous == null) emit(ProductsContent.Loading)
            val content =
                when (val result = repository.getProducts(venueIds)) {
                    is Result.Success -> {
                        val ui = result.data.toUi(venueNames())
                        shown = venueIds to ui
                        ProductsContent.Ready(ui)
                    }

                    is Result.Failure -> {
                        val message = result.error.toProductsMessage()
                        if (previous != null) ProductsContent.Ready(previous, refreshError = message) else ProductsContent.Failed(message)
                    }
                }
            refreshing.value = false
            emit(content)
        }

    private fun venueNames(): Map<String, String> =
        (venueRepository.venues.value as? VenueList.Loaded)?.venues?.associate { it.id to it.name }.orEmpty()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun DataError.toProductsMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        ProductsErrorCodes.CURRENCIES_DIFFER, ProductsErrorCodes.BUSINESS_DAYS_DIFFER -> {
            UiText.Resource(
                Res.string.sales_detail_error_venues_differ,
            )
        }

        ProductsErrorCodes.VENUE_NOT_FOUND -> {
            UiText.Resource(Res.string.sales_detail_error_venue_not_found)
        }

        else -> {
            toUiText()
        }
    }
