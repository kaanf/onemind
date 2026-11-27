package com.onemind.sales.presentation.losses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.core.presentation.util.toUiText
import com.onemind.core.presentation.venue.VenueTarget
import com.onemind.core.presentation.venue.venueTarget
import com.onemind.sales.domain.losses.LossesErrorCodes
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 02c Discounts & losses: loads the selected period for the selected scope, again on a period or scope
 * change and on pull-to-refresh (with `If-None-Match`, ADR 0005). Not a live screen: no polling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LossesViewModel(
    private val repository: LossesRepository,
    venueScope: VenueScopeRepository,
    private val venueRepository: VenueRepository,
) : ViewModel() {
    private val period = MutableStateFlow(LossesPeriod.WEEK)
    private val reloads = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)
    private var latestTarget: VenueTarget = VenueTarget.Waiting

    private val target: Flow<VenueTarget> =
        combine(venueScope.selected, venueRepository.venues, ::venueTarget).distinctUntilChanged()

    // The last figures per period and target, kept on screen while a refresh fails.
    private var shown: Pair<Pair<List<String>, LossesPeriod>, LossesUi>? = null

    val state: StateFlow<LossesState> =
        combine(
            combine(target, period, reloads) { target, period, _ -> target to period }
                .flatMapLatest { (target, period) ->
                    latestTarget = target
                    when (target) {
                        VenueTarget.Waiting -> flowOf(LossesContent.Loading)
                        VenueTarget.NoVenue -> flowOf(LossesContent.NoVenue)
                        is VenueTarget.VenuesFailed -> flowOf(LossesContent.Failed(target.error.toLossesMessage()))
                        is VenueTarget.Venues -> load(target.ids, period)
                    }.map { period to it }
                },
            period,
            refreshing,
        ) { (loadedPeriod, content), period, isRefreshing ->
            // A new period's tab shows at once; the previous period's figures never appear under it.
            LossesState(period, if (loadedPeriod == period) content else LossesContent.Loading, isRefreshing)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LossesState())

    fun onAction(action: LossesAction) {
        when (action) {
            is LossesAction.SelectPeriod -> {
                period.value = action.period
            }

            LossesAction.Refresh -> {
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

    private fun load(
        venueIds: List<String>,
        period: LossesPeriod,
    ): Flow<LossesContent> =
        flow {
            val key = venueIds to period
            val previous = shown?.takeIf { it.first == key }?.second
            if (previous == null) emit(LossesContent.Loading)
            val content =
                when (val result = repository.getLosses(venueIds, period)) {
                    is Result.Success -> {
                        val ui = result.data.toUi()
                        shown = key to ui
                        LossesContent.Ready(ui)
                    }

                    is Result.Failure -> {
                        val message = result.error.toLossesMessage()
                        if (previous != null) LossesContent.Ready(previous, refreshError = message) else LossesContent.Failed(message)
                    }
                }
            refreshing.value = false
            emit(content)
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun DataError.toLossesMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        LossesErrorCodes.CURRENCIES_DIFFER, LossesErrorCodes.BUSINESS_DAYS_DIFFER -> {
            UiText.Resource(
                Res.string.sales_detail_error_venues_differ,
            )
        }

        LossesErrorCodes.VENUE_NOT_FOUND -> {
            UiText.Resource(Res.string.sales_detail_error_venue_not_found)
        }

        else -> {
            toUiText()
        }
    }
