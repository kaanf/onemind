package com.onemind.today.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.core.domain.profile.ProfileRepository
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
import com.onemind.today.domain.CachedToday
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
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
    private val venueRepository: VenueRepository,
    profileRepository: ProfileRepository,
    private val clock: Clock,
    private val pollInterval: TodayPollInterval,
) : ViewModel() {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val refreshing = MutableStateFlow(false)

    /** What Today shows for the selected scope and the venue list (`docs/product/SCREENS.md` 01). */
    private val target: Flow<Target> =
        combine(venueScope.selected, venueRepository.venues) { scope, list -> target(scope, list) }.distinctUntilChanged()

    private val header: Flow<TodayHeader> =
        combine(venueScope.selected, venueRepository.venues, profileRepository.profile) { scope, list, profile ->
            TodayHeader(scopeName = scope?.let { scopeName(it, list) }, initials = profile?.initials)
        }

    private var latestTarget: Target = Target.Waiting

    /** The figures last on screen and this session's summary behind them, kept for a return to the screen. */
    private var shown: Shown? = null

    private class Shown(
        val venueIds: List<String>,
        val content: TodayContent.Ready,
        val summary: TodaySummary?,
    )

    val state: StateFlow<TodayState> =
        combine(
            target.flatMapLatest { target ->
                latestTarget = target
                when (target) {
                    Target.Waiting -> flowOf(TodayContent.Loading)
                    Target.NoVenue -> flowOf(TodayContent.NoVenue)
                    is Target.VenuesFailed -> flowOf(TodayContent.Failed(target.error.toTodayMessage()))
                    is Target.Venues -> poll(target.ids)
                }
            },
            refreshing,
            header,
        ) { content, isRefreshing, header -> TodayState(content, isRefreshing, header) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayState())

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.Refresh -> {
                if (!state.value.canRefresh) return
                refreshing.value = true
                if (latestTarget is Target.VenuesFailed) {
                    // The venue list itself failed: load it again (it also picks the default scope).
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

    private fun poll(venueIds: List<String>): Flow<TodayContent> =
        flow {
            // Back on the screen (a restart after WhileSubscribed stopped): the figures of the same scope stay
            // and refresh silently, like a poll. Otherwise (ADR 0006 §1) skeletons until the first answer, and
            // the figures saved on the device (always with their age) only when it fails or takes longer than
            // CachedFallbackDelay.
            val remembered = shown?.takeIf { it.venueIds == venueIds }
            emit(remembered?.content ?: TodayContent.Loading)
            val cached = repository.cachedToday(venueIds)
            var summary: TodaySummary? = remembered?.summary
            var first = remembered == null
            while (true) {
                var failure: DataError? = null
                val result =
                    if (first && cached != null) {
                        awaitWithCachedFallback({ repository.getToday(venueIds) }) {
                            val fallback = cached.content(offline = false, error = null)
                            shown = Shown(venueIds, fallback, summary = null)
                            emit(fallback)
                        }
                    } else {
                        repository.getToday(venueIds)
                    }
                first = false
                when (result) {
                    is Result.Success -> summary = result.data
                    is Result.Failure -> failure = result.error
                }
                // This answer also serves a refresh asked while it was on the way (pulling on cached figures).
                refreshRequests.tryReceive()
                refreshing.value = false
                val content = content(summary, cached, failure)
                if (content is TodayContent.Ready) shown = Shown(venueIds, content, summary)
                emit(content)
                // Wait for the next poll, redrawing the clock at each minute; a refresh request ends the wait.
                var remaining = pollInterval.after(summary)
                while (remaining > Duration.ZERO) {
                    val step = minOf(remaining, untilNextMinute())
                    if (withTimeoutOrNull(step) { refreshRequests.receive() } != null) break
                    remaining -= step
                    if (remaining > Duration.ZERO && content is TodayContent.Ready) emit(content(summary, cached, failure))
                }
            }
        }

    /**
     * What the latest poll leaves on screen: this session's figures (with a note when the poll failed);
     * else the cached ones with their age, marked offline after a network failure; else the error.
     */
    private fun content(
        summary: TodaySummary?,
        cached: CachedToday?,
        failure: DataError?,
    ): TodayContent {
        val error = failure?.toTodayMessage()
        return when {
            summary != null -> TodayContent.Ready(summary.toUi(clock.now()), error)
            cached != null -> cached.content(offline = failure is DataError.Network, error = error)
            else -> TodayContent.Failed(error ?: DataError.Network.UNKNOWN.toUiText())
        }
    }

    private fun CachedToday.content(
        offline: Boolean,
        error: UiText?,
    ): TodayContent.Ready {
        // Venues on one Today share a time zone (422 BUSINESS_DAYS_DIFFER otherwise).
        val zone = summary.venues.firstOrNull()?.timeZone
        val now = clock.now()
        return TodayContent.Ready(
            today = summary.toUi(now),
            refreshError = error.takeUnless { offline },
            cached =
                CachedMarker(
                    time = zone?.let { formatClockTime(savedAt, it) },
                    day = zone?.let { cachedDay(savedAt.toLocalDateTime(it).date, now.toLocalDateTime(it).date) } ?: CachedDay.Today,
                    offline = offline,
                ),
        )
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

private sealed interface Target {
    /** The venue list (or the default scope picked from it) isn't there yet. */
    data object Waiting : Target

    /** The account has no venues. */
    data object NoVenue : Target

    /** The venue list failed to load and none is known. */
    data class VenuesFailed(
        val error: DataError,
    ) : Target

    data class Venues(
        val ids: List<String>,
    ) : Target
}

private fun target(
    scope: VenueScope?,
    list: VenueList,
): Target =
    when (list) {
        VenueList.Loading -> {
            Target.Waiting
        }

        is VenueList.Failed -> {
            Target.VenuesFailed(list.error)
        }

        is VenueList.Loaded -> {
            val ids = scope?.venueIds(list.venues).orEmpty()
            when {
                list.venues.isEmpty() -> Target.NoVenue
                ids.isEmpty() -> Target.Waiting
                else -> Target.Venues(ids)
            }
        }
    }

private fun scopeName(
    scope: VenueScope,
    list: VenueList,
): ScopeName? =
    when (scope) {
        VenueScope.All -> ScopeName.AllVenues
        is VenueScope.Venue -> (list as? VenueList.Loaded)?.venues?.firstOrNull { it.id == scope.venueId }?.let { ScopeName.Venue(it.name) }
    }

/** Today, a weekday within the last week, or the date. */
internal fun cachedDay(
    saved: LocalDate,
    today: LocalDate,
): CachedDay {
    val daysAgo = saved.daysUntil(today)
    return when {
        daysAgo <= 0 -> CachedDay.Today
        daysAgo < DAYS_IN_A_WEEK -> CachedDay.Weekday(saved.dayOfWeek)
        else -> CachedDay.Date(saved)
    }
}

private const val DAYS_IN_A_WEEK = 7

private fun DataError.toTodayMessage(): UiText =
    when ((this as? DataError.Api)?.code) {
        TodayErrorCodes.CURRENCIES_DIFFER, TodayErrorCodes.BUSINESS_DAYS_DIFFER -> UiText.Resource(Res.string.today_error_venues_differ)
        TodayErrorCodes.VENUE_NOT_FOUND -> UiText.Resource(Res.string.today_error_venue_not_found)
        else -> toUiText()
    }
