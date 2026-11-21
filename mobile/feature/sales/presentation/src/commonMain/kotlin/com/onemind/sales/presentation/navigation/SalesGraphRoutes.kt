package com.onemind.sales.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface SalesGraphRoutes {
    @Serializable
    data object Graph : SalesGraphRoutes

    @Serializable
    data object Sales : SalesGraphRoutes
}
