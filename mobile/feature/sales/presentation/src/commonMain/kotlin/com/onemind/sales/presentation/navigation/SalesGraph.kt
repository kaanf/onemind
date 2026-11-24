package com.onemind.sales.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.sales.presentation.losses.LossesRoot
import com.onemind.sales.presentation.products.ProductsRoot
import com.onemind.sales.presentation.sales.SalesRoot

/** The Sales tab: 02 Sales, with entry points to 02a Products by hour and 02c Discounts & losses. */
fun NavGraphBuilder.salesGraph(navController: NavController) {
    navigation<SalesGraphRoutes.Graph>(
        startDestination = SalesGraphRoutes.Sales,
    ) {
        composable<SalesGraphRoutes.Sales> {
            SalesRoot(
                onOpenProductsByHour = { navController.navigate(SalesGraphRoutes.ProductsByHour) },
                onOpenLosses = { navController.navigate(SalesGraphRoutes.Losses) },
            )
        }
        composable<SalesGraphRoutes.ProductsByHour> {
            ProductsRoot(onBack = { navController.navigateUp() })
        }
        composable<SalesGraphRoutes.Losses> {
            LossesRoot(onBack = { navController.navigateUp() })
        }
    }
}
