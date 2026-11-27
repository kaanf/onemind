package com.onemind.staff.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.staff.presentation.staff.StaffRoot

/** The Staff tab: 03 Staff (03a Waiter detail comes next). */
fun NavGraphBuilder.staffGraph() {
    navigation<StaffGraphRoutes.Graph>(
        startDestination = StaffGraphRoutes.Staff,
    ) {
        composable<StaffGraphRoutes.Staff> {
            StaffRoot()
        }
    }
}
