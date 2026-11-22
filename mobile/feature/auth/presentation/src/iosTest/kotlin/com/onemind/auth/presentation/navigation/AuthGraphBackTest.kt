package com.onemind.auth.presentation.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real auth graph on a test controller: A2's back actions must always land on A1. */
class AuthGraphBackTest {
    private lateinit var navController: TestNavHostController

    @BeforeTest
    fun setUp() {
        navController = TestNavHostController()
        navController.graph = navController.createGraph(startDestination = AuthGraphRoutes.Graph) { authGraph(navController) }
        navController.navigate(AuthGraphRoutes.EmailCode(email = "owner@yourbar.cz", resendAfterSeconds = 30))
    }

    private fun onSignIn() = navController.currentDestination?.hasRoute(AuthGraphRoutes.SignIn::class) == true

    @Test
    fun useADifferentEmailReturnsToSignIn() {
        navController.backToSignIn()

        assertTrue(onSignIn())
    }

    @Test
    fun systemBackThenTheButtonStaysOnSignIn() {
        navController.popBackStack()
        navController.backToSignIn()

        assertTrue(onSignIn())
        assertTrue(navController.backStack.isNotEmpty())
    }

    @Test
    fun repeatedBackActionsNeverEmptyTheGraph() {
        repeat(3) { navController.backToSignIn() }

        assertTrue(onSignIn())
        assertEquals(1, navController.backStack.count { it.destination.hasRoute(AuthGraphRoutes.SignIn::class) })
    }

    @Test
    fun aPlainSecondPopWouldEmptyIt() {
        // The bug the review found: two plain pops leave no destination at all.
        navController.popBackStack()
        navController.popBackStack()

        assertEquals(null, navController.currentDestination)
    }
}
