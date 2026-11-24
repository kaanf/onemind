package com.onemind.composeapp.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.onemind.core.designsystem.components.TabBar
import com.onemind.core.designsystem.components.TabBarItem
import com.onemind.core.designsystem.theme.OneMindMotion
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.sales.presentation.navigation.SalesGraphRoutes
import com.onemind.sales.presentation.navigation.salesGraph
import com.onemind.staff.presentation.navigation.staffGraph
import com.onemind.today.presentation.navigation.todayGraph
import org.jetbrains.compose.resources.stringResource

/**
 * Root of the signed-in app: one graph per tab and the tab bar. Cross-feature shortcuts (e.g. Today →
 * Staff) are wired here as callbacks into the feature graphs when those screens exist.
 */
@Composable
fun NavigationRoot(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.topLevelDestination()

    Column(modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background)) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.graph,
            // Pushes slide in from the right and pops slide back out (predictive back follows the pop
            // transitions); a tab switch only crossfades.
            enterTransition = {
                if (isTabSwitch()) {
                    fadeIn(OneMindMotion.short())
                } else {
                    slideInHorizontally(OneMindMotion.enter()) { it } + fadeIn(OneMindMotion.enter())
                }
            },
            exitTransition = {
                if (isTabSwitch()) fadeOut(OneMindMotion.short()) else slideOutHorizontally(OneMindMotion.enter()) { -it / PARALLAX }
            },
            popEnterTransition = { slideInHorizontally(OneMindMotion.enter()) { -it / PARALLAX } },
            popExitTransition = { slideOutHorizontally(OneMindMotion.exit()) { it } + fadeOut(OneMindMotion.exit()) },
            modifier = Modifier.weight(1f),
        ) {
            todayGraph(onOpenLosses = { navController.navigate(SalesGraphRoutes.Losses) })
            salesGraph(navController)
            staffGraph()
            stockGraph()
        }

        if (currentTab != null) {
            val tabs = TopLevelDestination.entries
            TabBar(
                items = tabs.map { TabBarItem(stringResource(it.label), it.icon) },
                selectedIndex = currentTab.ordinal,
                onSelect = { index -> navController.navigateToTab(tabs[index]) },
            )
        }
    }
}

/** A move to another tab's first screen (tab bar), as opposed to opening a detail screen. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val target = targetState.destination
    val isTabRoot = target.parent?.startDestinationId == target.id
    return isTabRoot && initialState.destination.topLevelDestination() != target.topLevelDestination()
}

/** The screen underneath moves a quarter of the width while a detail screen slides over it. */
private const val PARALLAX = 4

/** The tab whose graph contains this destination, so detail screens keep their parent tab active. */
private fun NavDestination.topLevelDestination(): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { tab ->
        hierarchy.any { it.hasRoute(tab.graphClass) }
    }

/** Switches tabs keeping one back stack per tab: re-selecting a tab restores where the user was. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
