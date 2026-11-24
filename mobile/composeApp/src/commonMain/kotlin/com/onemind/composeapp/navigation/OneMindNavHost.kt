package com.onemind.composeapp.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigationevent.NavigationEvent
import com.onemind.core.designsystem.theme.OneMindMotion

/**
 * The app's only NavHost setup, so the auth gate and the tabs move the same way. A push slides in from
 * the right; every back (top-bar arrow, system back button, back gesture, iOS swipe) slides the screen
 * back out. A tab switch, and back from a tab's first screen to the previous tab, only crossfade.
 *
 * The predictive transitions must be set too: NavHost uses them while a back gesture is in progress,
 * and their defaults are a scale-and-fade on Android and an edge-dependent slide on iOS.
 */
@Composable
fun OneMindNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            if (crossfades(upper = targetState, under = initialState)) {
                fadeIn(OneMindMotion.short())
            } else {
                slideInHorizontally(OneMindMotion.enter()) { it } + fadeIn(OneMindMotion.enter())
            }
        },
        exitTransition = {
            if (crossfades(upper = targetState, under = initialState)) {
                fadeOut(OneMindMotion.short())
            } else {
                slideOutHorizontally(OneMindMotion.enter()) { -it / PARALLAX }
            }
        },
        popEnterTransition = { popEnter(fromRightEdge = false, gesture = false) },
        popExitTransition = { popExit(fromRightEdge = false, gesture = false) },
        predictivePopEnterTransition = { edge -> popEnter(fromRightEdge = edge == NavigationEvent.EDGE_RIGHT, gesture = true) },
        predictivePopExitTransition = { edge -> popExit(fromRightEdge = edge == NavigationEvent.EDGE_RIGHT, gesture = true) },
        builder = builder,
    )
}

/*
 * Back mirrors the push: the screen on top slides out to the right and the one underneath comes back
 * from a quarter left. A gesture from the right edge (Android) moves the other way, with the finger.
 * A button pop slides on the enter curve and fades only at the end (exit curve). A gesture slide follows the
 * finger linearly and never fades, so the screen underneath never shows through the one being dragged; back
 * from a tab's first screen by gesture fades the upper screen on the exit curve, opaque until late, over the
 * one underneath.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(
    fromRightEdge: Boolean,
    gesture: Boolean,
): EnterTransition =
    if (crossfades(upper = initialState, under = targetState)) {
        if (gesture) EnterTransition.None else fadeIn(OneMindMotion.short())
    } else {
        val direction = if (fromRightEdge) 1 else -1
        slideInHorizontally(if (gesture) OneMindMotion.gesture() else OneMindMotion.enter()) { direction * it / PARALLAX }
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(
    fromRightEdge: Boolean,
    gesture: Boolean,
): ExitTransition =
    if (crossfades(upper = initialState, under = targetState)) {
        fadeOut(if (gesture) OneMindMotion.exit() else OneMindMotion.short())
    } else if (gesture) {
        val direction = if (fromRightEdge) -1 else 1
        slideOutHorizontally(OneMindMotion.gesture()) { direction * it }
    } else {
        slideOutHorizontally(OneMindMotion.enter()) { it } + fadeOut(OneMindMotion.exit())
    }

private fun crossfades(
    upper: NavBackStackEntry,
    under: NavBackStackEntry,
): Boolean = isTabChange(upper = upper.destination, under = under.destination)

/**
 * True when moving between [upper] (the screen pushed, or the one being popped) and [under] changes
 * tabs at a tab's first screen: a tab-bar switch, or back from a tab's first screen to the previous
 * tab. Opening or closing a detail screen, even one in another tab (Today → Discounts & losses), slides.
 */
internal fun isTabChange(
    upper: NavDestination,
    under: NavDestination,
): Boolean {
    val isTabRoot = upper.parent?.startDestinationId == upper.id
    return isTabRoot && upper.topLevelDestination() != under.topLevelDestination()
}

/** The screen underneath moves a quarter of the width while a detail screen slides over it. */
private const val PARALLAX = 4
