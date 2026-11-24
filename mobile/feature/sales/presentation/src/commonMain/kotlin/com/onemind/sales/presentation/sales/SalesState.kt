package com.onemind.sales.presentation.sales

import com.onemind.core.presentation.util.UiText
import com.onemind.sales.domain.SalesPeriod

/** Screen 02 Sales (`docs/product/SCREENS.md#02`). */
data class SalesState(
    val period: SalesPeriod = SalesPeriod.WEEK,
    val content: SalesContent = SalesContent.Loading,
    val isRefreshing: Boolean = false,
    /** The selected venue's name, or `null` for *All venues* / not known yet. */
    val venueName: String? = null,
    val isAllVenues: Boolean = false,
) {
    val canRefresh: Boolean get() = content is SalesContent.Ready || content is SalesContent.Failed
}

sealed interface SalesContent {
    data object Loading : SalesContent

    /** The account has no venues. */
    data object NoVenue : SalesContent

    /** Nothing to show: the first load failed and nothing is cached. */
    data class Failed(
        val message: UiText,
    ) : SalesContent

    /** Figures, with a note when the latest poll failed, and the age of cached figures. */
    data class Ready(
        val sales: SalesUi,
        val refreshError: UiText? = null,
        val cached: SalesCachedMarker? = null,
    ) : SalesContent
}

/** Cached figures (ADR 0006): when they were saved (venue time), marked offline after a network failure. */
data class SalesCachedMarker(
    val time: String?,
    val offline: Boolean,
)

sealed interface SalesAction {
    data object Refresh : SalesAction

    data class SelectPeriod(
        val period: SalesPeriod,
    ) : SalesAction
}
