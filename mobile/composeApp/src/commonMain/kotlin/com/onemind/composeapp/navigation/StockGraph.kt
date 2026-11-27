package com.onemind.composeapp.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.composeapp.generated.resources.Res
import com.onemind.composeapp.generated.resources.stock_placeholder_message
import com.onemind.composeapp.generated.resources.stock_title
import com.onemind.composeapp.stock.StockDemoScreen
import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.designsystem.components.PlaceholderScreen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The Stock tab. Lives here until `feature:stock` exists (phase 6); its first-release landing has no
 * design yet (`docs/product/MVP_SCOPE.md#stock-tab-in-the-first-release`). Dev builds show the canvas
 * `StockPlan` landing over demo figures ([StockDemoScreen], owner decision 2026-11-27 for demos); prod
 * keeps the placeholder, as there is no stock data yet (rule 2).
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
            val environment = koinInject<ApiConfig>().environment
            if (environment == AppEnvironment.DEV) {
                StockDemoScreen()
            } else {
                PlaceholderScreen(
                    title = stringResource(Res.string.stock_title),
                    message = stringResource(Res.string.stock_placeholder_message),
                )
            }
        }
    }
}
