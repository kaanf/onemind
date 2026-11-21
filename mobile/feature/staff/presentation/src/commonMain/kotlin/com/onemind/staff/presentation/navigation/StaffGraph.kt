package com.onemind.staff.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.core.designsystem.components.PlaceholderScreen
import com.onemind.staff.presentation.generated.resources.Res
import com.onemind.staff.presentation.generated.resources.staff_placeholder_message
import com.onemind.staff.presentation.generated.resources.staff_title
import org.jetbrains.compose.resources.stringResource

/** The Staff tab. Placeholder until 03 Staff and 03a Waiter detail are built (phase 5). */
fun NavGraphBuilder.staffGraph() {
    navigation<StaffGraphRoutes.Graph>(
        startDestination = StaffGraphRoutes.Staff,
    ) {
        composable<StaffGraphRoutes.Staff> {
            PlaceholderScreen(
                title = stringResource(Res.string.staff_title),
                message = stringResource(Res.string.staff_placeholder_message),
            )
        }
    }
}
