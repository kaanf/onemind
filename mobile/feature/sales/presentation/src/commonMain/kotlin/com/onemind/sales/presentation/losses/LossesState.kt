package com.onemind.sales.presentation.losses

import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.losses.LossesPeriod

data class LossesState(
    val period: LossesPeriod = LossesPeriod.WEEK,
    val content: LossesContent = LossesContent.Loading,
    /** A refresh request is running while figures stay on screen. */
    val isRefreshing: Boolean = false,
) {
    val canRefresh: Boolean get() = content is LossesContent.Ready || content is LossesContent.Failed
}

sealed interface LossesContent {
    data object Loading : LossesContent

    /** The account has no venues yet. */
    data object NoVenue : LossesContent

    /** The load failed and there is nothing to show. */
    data class Failed(
        val message: UiText,
    ) : LossesContent

    /** Figures to show; [refreshError] says the latest refresh failed and these are from the one before. */
    data class Ready(
        val losses: LossesUi,
        val refreshError: UiText? = null,
    ) : LossesContent
}

sealed interface LossesAction {
    data class SelectPeriod(
        val period: LossesPeriod,
    ) : LossesAction

    data object Refresh : LossesAction
}
