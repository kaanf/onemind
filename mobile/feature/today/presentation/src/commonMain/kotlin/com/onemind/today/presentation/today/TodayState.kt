package com.onemind.today.presentation.today

import com.onemind.core.presentation.util.UiText

data class TodayState(
    val content: TodayContent = TodayContent.Loading,
    /** A pull-to-refresh request is running. */
    val isRefreshing: Boolean = false,
) {
    val canRefresh: Boolean get() = content is TodayContent.Ready || content is TodayContent.Failed
}

sealed interface TodayContent {
    data object Loading : TodayContent

    /** No venue is selected: the account has no venues yet, or the venue list hasn't loaded. */
    data object NoVenue : TodayContent

    /** The first load failed: nothing to show yet. */
    data class Failed(
        val message: UiText,
    ) : TodayContent

    /** Figures to show; [refreshError] says the latest poll failed and these are from the one before. */
    data class Ready(
        val today: TodayUi,
        val refreshError: UiText? = null,
    ) : TodayContent
}

sealed interface TodayAction {
    /** Pull-to-refresh or *Try again*: poll now instead of waiting for the next tick. */
    data object Refresh : TodayAction
}
