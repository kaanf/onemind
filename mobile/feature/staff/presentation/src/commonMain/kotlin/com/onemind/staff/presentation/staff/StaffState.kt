package com.onemind.staff.presentation.staff

import com.onemind.core.presentation.util.UiText

data class StaffState(
    val content: StaffContent = StaffContent.Loading,
    /** A refresh request is running while figures stay on screen. */
    val isRefreshing: Boolean = false,
) {
    val canRefresh: Boolean get() = content is StaffContent.Ready || content is StaffContent.Failed
}

sealed interface StaffContent {
    data object Loading : StaffContent

    data object NoVenue : StaffContent

    data class Failed(
        val message: UiText,
    ) : StaffContent

    data class Ready(
        val staff: StaffUi,
        val refreshError: UiText? = null,
    ) : StaffContent
}

sealed interface StaffAction {
    data object Refresh : StaffAction
}
