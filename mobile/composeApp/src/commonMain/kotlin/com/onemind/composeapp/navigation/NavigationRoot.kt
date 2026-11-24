package com.onemind.composeapp.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.onemind.core.designsystem.components.TabBar
import com.onemind.core.designsystem.components.TabBarItem
import com.onemind.core.designsystem.theme.OneMindTheme
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
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            modifier = Modifier.weight(1f),
        ) {
            todayGraph()
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
