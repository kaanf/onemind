package com.onemind.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Motion tokens: every transition in the app takes its duration and easing from here (no ad-hoc
 * `tween(…)` numbers in screens). Enter uses the emphasized decelerate curve, exit the accelerate one.
 */
object OneMindMotion {
    /** Tab switch, chip selection, small state changes. */
    const val SHORT_MILLIS = 150

    /** Skeleton → content, expand / collapse. */
    const val MEDIUM_MILLIS = 250

    /** Screen push and pop. */
    const val LONG_MILLIS = 350

    /** One skeleton pulse (half a cycle). */
    const val PULSE_MILLIS = 500

    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> short(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS, easing = StandardEasing)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS, easing = StandardEasing)

    fun <T> enter(): FiniteAnimationSpec<T> = tween(LONG_MILLIS, easing = EnterEasing)

    fun <T> exit(): FiniteAnimationSpec<T> = tween(LONG_MILLIS, easing = ExitEasing)
}
