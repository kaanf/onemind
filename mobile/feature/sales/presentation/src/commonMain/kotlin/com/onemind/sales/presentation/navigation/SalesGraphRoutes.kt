package com.onemind.sales.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface SalesGraphRoutes {
    @Serializable
    data object Graph : SalesGraphRoutes

    @Serializable
    data object Sales : SalesGraphRoutes

    /** 02a Products by hour. */
    @Serializable
    data object ProductsByHour : SalesGraphRoutes

    /** 02c Discounts & losses. */
    @Serializable
    data object Losses : SalesGraphRoutes
}
