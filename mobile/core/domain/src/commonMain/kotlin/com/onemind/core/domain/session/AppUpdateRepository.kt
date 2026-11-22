package com.onemind.core.domain.session

import kotlinx.coroutines.flow.StateFlow

/** The API answered `426 APP_UPGRADE_REQUIRED`; [minimumVersion] is `params.minimumVersion` if sent. */
data class AppUpdateRequired(
    val minimumVersion: String?,
)

/** App-wide: once any call gets `426`, the app shows the update screen until a retry passes. */
interface AppUpdateRepository {
    val required: StateFlow<AppUpdateRequired?>

    /** Hides the update screen so the next call checks again ("Check again"). */
    fun dismiss()
}
