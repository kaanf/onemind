package com.onemind.today.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface TodayGraphRoutes {
    @Serializable
    data object Graph : TodayGraphRoutes

    @Serializable
    data object Today : TodayGraphRoutes
}
