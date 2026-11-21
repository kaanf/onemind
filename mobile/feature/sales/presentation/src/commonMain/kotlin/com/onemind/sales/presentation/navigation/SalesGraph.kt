package com.onemind.sales.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.sales.presentation.generated.resources.Res
import com.onemind.sales.presentation.generated.resources.sales_placeholder_message
import com.onemind.sales.presentation.generated.resources.sales_title
import org.jetbrains.compose.resources.stringResource

/** The Sales tab. Placeholder until 02 Sales and 02a–02c are built (phase 5). */
fun NavGraphBuilder.salesGraph() {
    navigation<SalesGraphRoutes.Graph>(
        startDestination = SalesGraphRoutes.Sales,
    ) {
        composable<SalesGraphRoutes.Sales> {
            PlaceholderScreen(
                title = stringResource(Res.string.sales_title),
                message = stringResource(Res.string.sales_placeholder_message),
            )
        }
    }
}
