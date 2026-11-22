package com.onemind.composeapp.root

import com.onemind.core.domain.session.AppUpdateRequired
import com.onemind.core.domain.session.SessionState

/** What the app root shows. The update screen wins over everything, sign-in included. */
sealed interface AppScreen {
    data class Update(
        val minimumVersion: String?,
    ) : AppScreen

    /** Secure storage is still being read: a blank background, so A1 never flashes for a signed-in user. */
    data object Loading : AppScreen

    data object Auth : AppScreen

    data object Main : AppScreen
}

fun appScreen(
    session: SessionState,
    update: AppUpdateRequired?,
): AppScreen =
    when {
        update != null -> AppScreen.Update(update.minimumVersion)
        session == SessionState.Unknown -> AppScreen.Loading
        session == SessionState.SignedOut -> AppScreen.Auth
        else -> AppScreen.Main
    }
