package com.onemind.sales.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.sales.presentation.losses.LossesRoot
import com.onemind.sales.presentation.sales.SalesRoot

/** The Sales tab: 02 Sales, with entry points to 02a Products by hour and 02c Discounts & losses. */
fun NavGraphBuilder.salesGraph(navController: NavController) {
    navigation<SalesGraphRoutes.Graph>(
        startDestination = SalesGraphRoutes.Sales,
    ) {
        composable<SalesGraphRoutes.Sales> {
            SalesRoot(
                onOpenProductsByHour = { navController.navigateIfBuilt(SalesGraphRoutes.ProductsByHour) },
                onOpenLosses = { navController.navigate(SalesGraphRoutes.Losses) },
            )
        }
        composable<SalesGraphRoutes.Losses> {
            LossesRoot(onBack = { navController.navigateUp() })
        }
    }
}

/**
 * 02a is added to this graph in its own change; until then a tap does nothing instead of crashing on a
 * destination that isn't in the graph.
 */
private fun NavController.navigateIfBuilt(route: SalesGraphRoutes) {
    try {
        navigate(route)
    } catch (_: IllegalArgumentException) {
        Unit
    }
}
