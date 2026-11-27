package com.onemind.staff.presentation.staff

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
import com.onemind.staff.domain.StaffErrorCodes
import com.onemind.staff.domain.StaffRepository
import com.onemind.staff.presentation.generated.resources.Res
import com.onemind.staff.presentation.generated.resources.staff_error_venue_not_found
import com.onemind.staff.presentation.generated.resources.staff_error_venues_differ
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 03 Staff. Polls `GET /dashboard/staff` for the selected venue scope every [pollInterval] (60 s, ADR 0005)
 * only while the screen collects [state] (visible and in the foreground), with `If-None-Match`. A scope
 * change or pull-to-refresh starts a new poll. A failed poll keeps the last figures with a note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModel(
    private val repository: StaffRepository,
    venueScope: VenueScopeRepository,
    private val venueRepository: VenueRepository,
    private val pollInterval: Duration = 60.seconds,
) : ViewModel() {
    private val reloads = MutableStateFlow(0)
    private val refreshing = MutableStateFlow(false)
    private var latestTarget: VenueTarget = VenueTarget.Waiting

    private val target: Flow<VenueTarget> =
        combine(venueScope.selected, venueRepository.venues, ::venueTarget).distinctUntilChanged()

    // The last figures per venue set, kept on screen while a poll fails.
    private var shown: Pair<List<String>, StaffUi>? = null

    val state: StateFlow<StaffState> =
        combine(
            combine(target, reloads) { target, _ -> target }
                .flatMapLatest { target ->
                    latestTarget = target
                    when (target) {
                        VenueTarget.Waiting -> flowOf(StaffContent.Loading)
                        VenueTarget.NoVenue -> flowOf(StaffContent.NoVenue)
                        is VenueTarget.VenuesFailed -> flowOf(StaffContent.Failed(target.error.toStaffMessage()))
                        is VenueTarget.Venues -> poll(target.ids)
                    }
                },
            refreshing,
        ) { content, isRefreshing -> StaffState(content, isRefreshing) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StaffState())

    fun onAction(action: StaffAction) {
        when (action) {
            StaffAction.Refresh -> {
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

    private fun poll(venueIds: List<String>): Flow<StaffContent> =
        flow {
            if (shown?.first != venueIds) emit(StaffContent.Loading)
            while (true) {
                val previous = shown?.takeIf { it.first == venueIds }?.second
                val content =
                    when (val result = repository.getStaff(venueIds)) {
                        is Result.Success -> {
                            val ui = result.data.toUi()
                            shown = venueIds to ui
                            StaffContent.Ready(ui)
                        }

                        is Result.Failure -> {
                            val message = result.error.toStaffMessage()
                            if (previous != null) StaffContent.Ready(previous, refreshError = message) else StaffContent.Failed(message)
                        }
                    }
                refreshing.value = false
                emit(content)
                delay(pollInterval)
            }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun DataError.toStaffMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        StaffErrorCodes.CURRENCIES_DIFFER, StaffErrorCodes.BUSINESS_DAYS_DIFFER -> UiText.Resource(Res.string.staff_error_venues_differ)
        StaffErrorCodes.VENUE_NOT_FOUND -> UiText.Resource(Res.string.staff_error_venue_not_found)
        else -> toUiText()
    }
