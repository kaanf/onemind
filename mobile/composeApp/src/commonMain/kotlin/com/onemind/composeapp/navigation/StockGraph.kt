package com.onemind.composeapp.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.composeapp.generated.resources.Res
import com.onemind.composeapp.generated.resources.stock_placeholder_message
import com.onemind.composeapp.generated.resources.stock_title
import com.onemind.core.designsystem.components.PlaceholderScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/**
 * The Stock tab. Lives here until `feature:stock` exists (phase 6); its first-release landing has no
 * design yet (`docs/product/MVP_SCOPE.md#stock-tab-in-the-first-release`).
 */
sealed interface StockGraphRoutes {
    @Serializable
    data object Graph : StockGraphRoutes

    @Serializable
    data object Stock : StockGraphRoutes
}

fun NavGraphBuilder.stockGraph() {
    navigation<StockGraphRoutes.Graph>(
        startDestination = StockGraphRoutes.Stock,
    ) {
        composable<StockGraphRoutes.Stock> {
            PlaceholderScreen(
                title = stringResource(Res.string.stock_title),
                message = stringResource(Res.string.stock_placeholder_message),
            )
        }
    }
}
