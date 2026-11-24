package com.onemind.sales.presentation.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueList
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.domain.venue.venueIds
import com.onemind.core.presentation.format.formatClockTime
import com.onemind.core.presentation.util.UiText
import com.onemind.core.presentation.util.awaitWithCachedFallback
import com.onemind.core.presentation.util.toUiText
import com.onemind.sales.domain.CachedSales
import com.onemind.sales.domain.SalesErrorCodes
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRepository
import com.onemind.sales.domain.SalesSummary
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_error_venue_not_found
import com.onemind.sales.presentation.generated.resources.sales_error_venues_differ
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 02 Sales. Polls `GET /dashboard/sales` for the selected venue scope and period only while the screen
 * collects [state] (visible and in the foreground, ADR 0005), every [SalesPollInterval]. Changing the
 * period starts a new poll. A failed poll keeps the last figures with a note; cached figures (ADR 0006)
 * show with their age when the first load fails or is slow; a failed first load without them shows the error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModel(
    private val repository: SalesRepository,
    venueScope: VenueScopeRepository,
    private val venueRepository: VenueRepository,
    private val pollInterval: SalesPollInterval,
) : ViewModel() {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val refreshing = MutableStateFlow(false)
    private val period = MutableStateFlow(SalesPeriod.WEEK)

    private val target: Flow<SalesTarget> =
        combine(venueScope.selected, venueRepository.venues) { scope, list -> salesTarget(scope, list) }.distinctUntilChanged()

    private val scopeLabel: Flow<Pair<String?, Boolean>> =
        combine(venueScope.selected, venueRepository.venues) { scope, list ->
            when (scope) {
                null -> null to false
                VenueScope.All -> null to true
                is VenueScope.Venue -> (list as? VenueList.Loaded)?.venues?.firstOrNull { it.id == scope.venueId }?.name to false
            }
        }

    private var latestTarget: SalesTarget = SalesTarget.Waiting

    val state: StateFlow<SalesState> =
        combine(
            combine(target, period) { target, period -> target to period }.flatMapLatest { (target, period) ->
                latestTarget = target
                when (target) {
                    SalesTarget.Waiting -> flowOf(SalesContent.Loading)
                    SalesTarget.NoVenue -> flowOf(SalesContent.NoVenue)
                    is SalesTarget.VenuesFailed -> flowOf(SalesContent.Failed(target.error.toSalesMessage()))
                    is SalesTarget.Venues -> poll(target.ids, period)
                }
            },
            period,
            refreshing,
            scopeLabel,
        ) { content, period, isRefreshing, (venueName, all) ->
            SalesState(period = period, content = content, isRefreshing = isRefreshing, venueName = venueName, isAllVenues = all)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SalesState())

    fun onAction(action: SalesAction) {
        when (action) {
            is SalesAction.SelectPeriod -> {
                period.value = action.period
            }

            SalesAction.Refresh -> {
                if (!state.value.canRefresh) return
                refreshing.value = true
                if (latestTarget is SalesTarget.VenuesFailed) {
                    viewModelScope.launch {
                        venueRepository.refresh()
                        refreshing.value = false
                    }
                } else {
                    refreshRequests.trySend(Unit)
                }
            }
        }
    }

    private fun poll(
        venueIds: List<String>,
        period: SalesPeriod,
    ): Flow<SalesContent> =
        flow {
            emit(SalesContent.Loading)
            // ADR 0006 §1: skeletons until the first answer; cached figures only after a failure or a slow answer.
            val cached = repository.cachedSales(venueIds, period)
            var summary: SalesSummary? = null
            var first = true
            while (true) {
                var failure: DataError? = null
                val result =
                    if (first && cached != null) {
                        awaitWithCachedFallback(
                            { repository.getSales(venueIds, period) },
                        ) { emit(cached.content(offline = false, error = null)) }
                    } else {
                        repository.getSales(venueIds, period)
                    }
                first = false
                when (result) {
                    is Result.Success -> summary = result.data
                    is Result.Failure -> failure = result.error
                }
                // This answer also serves a refresh asked while it was on the way (pulling on cached figures).
                refreshRequests.tryReceive()
                refreshing.value = false
                emit(content(summary, cached, failure))
                withTimeoutOrNull(pollInterval.next()) { refreshRequests.receive() }
            }
        }

    private fun content(
        summary: SalesSummary?,
        cached: CachedSales?,
        failure: DataError?,
    ): SalesContent {
        val error = failure?.toSalesMessage()
        return when {
            summary != null -> SalesContent.Ready(summary.toUi(), error)
            cached != null -> cached.content(offline = failure is DataError.Network, error = error)
            else -> SalesContent.Failed(error ?: DataError.Network.UNKNOWN.toUiText())
        }
    }

    private fun CachedSales.content(
        offline: Boolean,
        error: UiText?,
    ): SalesContent.Ready {
        val zone = summary.venues.firstOrNull()?.timeZone
        return SalesContent.Ready(
            sales = summary.toUi(),
            refreshError = error.takeUnless { offline },
            cached = SalesCachedMarker(time = zone?.let { formatClockTime(savedAt, it) }, offline = offline),
        )
    }

    private companion object {
        /** Keeps polling through a configuration change, not through a stay in the background. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private sealed interface SalesTarget {
    data object Waiting : SalesTarget

    data object NoVenue : SalesTarget

    data class VenuesFailed(
        val error: DataError,
    ) : SalesTarget

    data class Venues(
        val ids: List<String>,
    ) : SalesTarget
}

private fun salesTarget(
    scope: VenueScope?,
    list: VenueList,
): SalesTarget =
    when (list) {
        VenueList.Loading -> {
            SalesTarget.Waiting
        }

        is VenueList.Failed -> {
            SalesTarget.VenuesFailed(list.error)
        }

        is VenueList.Loaded -> {
            val ids = scope?.venueIds(list.venues).orEmpty()
            when {
                list.venues.isEmpty() -> SalesTarget.NoVenue
                ids.isEmpty() -> SalesTarget.Waiting
                else -> SalesTarget.Venues(ids)
            }
        }
    }

private fun DataError.toSalesMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        SalesErrorCodes.CURRENCIES_DIFFER, SalesErrorCodes.BUSINESS_DAYS_DIFFER -> UiText.Resource(Res.string.sales_error_venues_differ)
        SalesErrorCodes.VENUE_NOT_FOUND -> UiText.Resource(Res.string.sales_error_venue_not_found)
        else -> toUiText()
    }
