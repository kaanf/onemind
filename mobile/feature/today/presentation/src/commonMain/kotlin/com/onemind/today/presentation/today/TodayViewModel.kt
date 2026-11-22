package com.onemind.today.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.domain.venue.VenueScope
import com.onemind.core.domain.venue.VenueScopeRepository
import com.onemind.core.presentation.util.UiText
import com.onemind.core.presentation.util.toUiText
import com.onemind.today.domain.TodayErrorCodes
import com.onemind.today.domain.TodayRepository
import com.onemind.today.domain.TodaySummary
import com.onemind.today.presentation.generated.resources.Res
import com.onemind.today.presentation.generated.resources.today_error_venue_not_found
import com.onemind.today.presentation.generated.resources.today_error_venues_differ
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * 01 Today. Polls `GET /dashboard/today` for the selected venue scope only while the screen collects
 * [state] (`collectAsStateWithLifecycle`: visible and in the foreground, ADR 0005), every
 * [TodayPollInterval], and redraws the clock each minute in between. A failed poll keeps the last
 * figures on screen with a note; a failed first load shows the error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val repository: TodayRepository,
    venueScope: VenueScopeRepository,
    private val clock: Clock,
    private val pollInterval: TodayPollInterval,
) : ViewModel() {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<TodayState> =
        combine(
            venueScope.selected.flatMapLatest { scope -> scope?.venueIds()?.let(::poll) ?: flowOf(TodayContent.NoVenue) },
            refreshing,
        ) { content, isRefreshing -> TodayState(content, isRefreshing) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayState())

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.Refresh -> {
                if (!state.value.canRefresh) return
                refreshing.value = true
                refreshRequests.trySend(Unit)
            }
        }
    }

    private fun poll(venueIds: List<String>): Flow<TodayContent> =
        flow {
            emit(TodayContent.Loading)
            var summary: TodaySummary? = null
            while (true) {
                var error: UiText? = null
                when (val result = repository.getToday(venueIds)) {
                    is Result.Success -> summary = result.data
                    is Result.Failure -> error = result.error.toTodayMessage()
                }
                refreshing.value = false
                val shown = summary
                if (shown == null) {
                    emit(TodayContent.Failed(error ?: DataError.Network.UNKNOWN.toUiText()))
                } else {
                    emit(TodayContent.Ready(shown.toUi(clock.now()), error))
                }
                // Wait for the next poll, redrawing the clock at each minute; a refresh request ends the wait.
                var remaining = pollInterval.after(summary)
                while (remaining > Duration.ZERO) {
                    val step = minOf(remaining, untilNextMinute())
                    if (withTimeoutOrNull(step) { refreshRequests.receive() } != null) break
                    remaining -= step
                    if (remaining > Duration.ZERO && shown != null) emit(TodayContent.Ready(shown.toUi(clock.now()), error))
                }
            }
        }

    private fun untilNextMinute(): Duration {
        val intoMinute = clock.now().toEpochMilliseconds().mod(1.minutes.inWholeMilliseconds)
        return (1.minutes.inWholeMilliseconds - intoMinute).milliseconds
    }

    private companion object {
        /** Keeps polling through a configuration change, not through a stay in the background. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The venues behind a scope. **Interim:** *All venues* needs the venue list (`GET /venues`, not in the
 * API yet), so it resolves to none and the screen shows the no-venue state until that list exists.
 */
private fun VenueScope.venueIds(): List<String>? =
    when (this) {
        is VenueScope.Venue -> listOf(venueId)
        VenueScope.All -> null
    }

private fun DataError.toTodayMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        TodayErrorCodes.CURRENCIES_DIFFER, TodayErrorCodes.BUSINESS_DAYS_DIFFER -> UiText.Resource(Res.string.today_error_venues_differ)
        TodayErrorCodes.VENUE_NOT_FOUND -> UiText.Resource(Res.string.today_error_venue_not_found)
        else -> toUiText()
    }
