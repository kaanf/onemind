package com.onemind.composeapp.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.onemind.sales.presentation.navigation.SalesGraphRoutes
import com.onemind.staff.presentation.navigation.staffGraph
import com.onemind.today.presentation.navigation.TodayGraphRoutes
import com.onemind.today.presentation.navigation.todayGraph
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which moves crossfade (tab changes) and which slide (detail screens); a back gesture always slides. */
class TabChangeTest {
    private val graph: NavGraph =
        NavigatorProvider()
            .apply {
                addNavigator(NavGraphNavigator(this))
                addNavigator(ComposeNavigator())
            }.navigation(startDestination = TopLevelDestination.Today.graph) {
                todayGraph(onOpenLosses = {})
                // salesGraph needs a NavController; the same shape: tab root first, then details.
                navigation<SalesGraphRoutes.Graph>(startDestination = SalesGraphRoutes.Sales) {
                    composable<SalesGraphRoutes.Sales> {}
                    composable<SalesGraphRoutes.Losses> {}
                }
                staffGraph()
                stockGraph()
            }

    private fun destination(route: KClass<*>): NavDestination {
        fun NavGraph.find(): NavDestination? = firstNotNullOfOrNull { if (it.hasRoute(route)) it else (it as? NavGraph)?.find() }
        return checkNotNull(graph.find()) { "$route is not in the graph" }
    }

    private val today = destination(TodayGraphRoutes.Today::class)
    private val sales = destination(SalesGraphRoutes.Sales::class)
    private val losses = destination(SalesGraphRoutes.Losses::class)
    private val stock = destination(StockGraphRoutes.Stock::class)

    @Test
    fun tabBarSwitchCrossfades() {
        assertTrue(isTabChange(upper = today, under = stock))
        // A tab tap from a detail screen lands on the other tab's first screen.
        assertTrue(isTabChange(upper = stock, under = losses))
    }

    @Test
    fun backByButtonFromATabsFirstScreenToThePreviousTabCrossfades() {
        assertTrue(isTabChange(upper = sales, under = today))
    }

    @Test
    fun openingAndClosingADetailScreenSlides() {
        assertFalse(isTabChange(upper = losses, under = sales))
    }

    @Test
    fun aDetailScreenInAnotherTabSlides() {
        // Today → Discounts & losses (Sales tab) and back.
        assertFalse(isTabChange(upper = losses, under = today))
    }
}
