package com.onemind.auth.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.onemind.auth.presentation.code.EmailCodeRoot
import com.onemind.auth.presentation.signin.SignInRoot

/**
 * A1 → A2. A successful verify signs in; the app gate in `composeApp` then replaces this graph with
 * the tab shell (A3–A5 onboarding comes with phase 4).
 */
fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation<AuthGraphRoutes.Graph>(startDestination = AuthGraphRoutes.SignIn) {
        composable<AuthGraphRoutes.SignIn> {
            SignInRoot(
                onCodeSent = { email, resendAfterSeconds ->
                    navController.navigate(AuthGraphRoutes.EmailCode(email, resendAfterSeconds)) { launchSingleTop = true }
                },
            )
        }
        composable<AuthGraphRoutes.EmailCode> { entry ->
            val route = entry.toRoute<AuthGraphRoutes.EmailCode>()
            EmailCodeRoot(
                email = route.email,
                resendAfterSeconds = route.resendAfterSeconds,
                onBack = navController::backToSignIn,
            )
        }
    }
}

/** Back to A1, never past it: a second back (system back + button) must not empty the graph. */
internal fun NavController.backToSignIn() {
    popBackStack<AuthGraphRoutes.SignIn>(inclusive = false)
}
