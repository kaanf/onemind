package com.onemind.today.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.today.presentation.generated.resources.Res
import com.onemind.today.presentation.generated.resources.today_placeholder_message
import com.onemind.today.presentation.generated.resources.today_title
import org.jetbrains.compose.resources.stringResource

/** The Today tab. Placeholder until 01 Today and 01a Tables are built (phase 5). */
fun NavGraphBuilder.todayGraph() {
    navigation<TodayGraphRoutes.Graph>(
        startDestination = TodayGraphRoutes.Today,
    ) {
        composable<TodayGraphRoutes.Today> {
            PlaceholderScreen(
                title = stringResource(Res.string.today_title),
                message = stringResource(Res.string.today_placeholder_message),
            )
        }
    }
}
