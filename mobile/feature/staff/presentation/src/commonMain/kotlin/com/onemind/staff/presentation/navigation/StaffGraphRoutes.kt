package com.onemind.staff.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface StaffGraphRoutes {
    @Serializable
    data object Graph : StaffGraphRoutes

    @Serializable
    data object Staff : StaffGraphRoutes
}
