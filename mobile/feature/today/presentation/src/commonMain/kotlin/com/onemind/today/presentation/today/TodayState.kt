package com.onemind.today.presentation.today

import com.onemind.core.presentation.util.UiText

data class TodayState(
    val content: TodayContent = TodayContent.Loading,
    /** A pull-to-refresh request is running. */
    val isRefreshing: Boolean = false,
    val header: TodayHeader = TodayHeader(),
) {
    val canRefresh: Boolean get() = content is TodayContent.Ready || content is TodayContent.Failed
}

/** The row above the date: the scope's name (switcher button) and the account initials. */
data class TodayHeader(
    /** `null` while the venue list loads: the button isn't drawn. */
    val scopeName: ScopeName? = null,
    /** From `GET /me`; `null` hides the account button (no invented initials, rule 2). */
    val initials: String? = null,
)

sealed interface ScopeName {
    data class Venue(
        val name: String,
    ) : ScopeName

    data object AllVenues : ScopeName
}

sealed interface TodayContent {
    data object Loading : TodayContent

    /** The account has no venues yet. */
    data object NoVenue : TodayContent

    /** The first load failed: nothing to show yet. */
    data class Failed(
        val message: UiText,
    ) : TodayContent

    /**
     * Figures to show; [refreshError] says the latest poll failed and these are from the one before.
     * [cached] is set when the figures come from the device's read cache: never shown without it.
     */
    data class Ready(
        val today: TodayUi,
        val refreshError: UiText? = null,
        val cached: CachedMarker? = null,
    ) : TodayContent
}

/** "Last updated 21:40" (and "· offline" after a network failure) on cached figures (ADR 0006, rule 2). */
data class CachedMarker(
    /** When the cached answer was received, in the venue's time zone. */
    val savedAt: String?,
    val offline: Boolean,
)

sealed interface TodayAction {
    /** Pull-to-refresh or *Try again*: poll now instead of waiting for the next tick. */
    data object Refresh : TodayAction
}
