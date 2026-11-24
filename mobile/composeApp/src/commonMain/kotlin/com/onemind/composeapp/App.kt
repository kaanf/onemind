package com.onemind.composeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.onemind.auth.presentation.navigation.AuthGraphRoutes
import com.onemind.auth.presentation.navigation.authGraph
import com.onemind.composeapp.navigation.NavigationRoot
import com.onemind.composeapp.navigation.OneMindNavHost
import com.onemind.composeapp.root.AppScreen
import com.onemind.composeapp.root.UpdateScreen
import com.onemind.composeapp.root.appScreen
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.domain.session.AppUpdateRepository
import com.onemind.core.domain.session.SessionRepository
import org.koin.compose.koinInject

/**
 * Root of the shared UI. Koin is started by the host before this runs (`initKoin`). The session
 * decides between sign-in and the tab shell; each gets its own back stack, so signing out (or a
 * refresh that fails) always lands on a fresh A1 and signing in on Today.
 */
@Composable
fun App() {
    OneMindTheme {
        AppRoot()
    }
}

@Composable
private fun AppRoot(
    sessions: SessionRepository = koinInject(),
    appUpdates: AppUpdateRepository = koinInject(),
) {
    val session by sessions.state.collectAsStateWithLifecycle()
    val update by appUpdates.required.collectAsStateWithLifecycle()

    when (val screen = appScreen(session, update)) {
        is AppScreen.Update -> {
            UpdateScreen(minimumVersion = screen.minimumVersion, onCheckAgain = appUpdates::dismiss)
        }

        AppScreen.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background))
        }

        AppScreen.Auth -> {
            val navController = rememberNavController()
            OneMindNavHost(navController = navController, startDestination = AuthGraphRoutes.Graph) {
                authGraph(navController)
            }
        }

        AppScreen.Main -> {
            NavigationRoot(navController = rememberNavController())
        }
    }
}
