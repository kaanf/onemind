package com.onemind.today.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.onemind.today.presentation.today.TodayRoot

/** The Today tab: 01 Today (01a Tables comes with its own roadmap item). */
fun NavGraphBuilder.todayGraph() {
    navigation<TodayGraphRoutes.Graph>(
        startDestination = TodayGraphRoutes.Today,
    ) {
        composable<TodayGraphRoutes.Today> {
            TodayRoot()
        }
    }
}
