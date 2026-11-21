package com.onemind.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf<OneMindTypography> { error("Wrap the UI in OneMindTheme") }
private val LocalRadius = staticCompositionLocalOf { OneMindRadius() }
private val LocalSpacing = staticCompositionLocalOf { OneMindSpacing() }

/**
 * Root theme. Provides the OneMind tokens and maps them onto Material 3, so Material components that
 * are still used look consistent. Light only until dark mode is designed (open question P7).
 */
@Composable
fun OneMindTheme(content: @Composable () -> Unit) {
    val colors = LightColors
    val typography = oneMindTypography()
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalRadius provides OneMindRadius(),
        LocalSpacing provides OneMindSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            content = content,
        )
    }
}

object OneMindTheme {
    val colors: OneMindColors
        @Composable @ReadOnlyComposable
        get() = LocalColors.current

    val typography: OneMindTypography
        @Composable @ReadOnlyComposable
        get() = LocalTypography.current

    val radius: OneMindRadius
        @Composable @ReadOnlyComposable
        get() = LocalRadius.current

    val spacing: OneMindSpacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}

private fun OneMindColors.toMaterialColorScheme() =
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = textPrimary,
        secondary = textPrimary,
        onSecondary = surface,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = textSecondary,
        outline = borderStrong,
        outlineVariant = divider,
        error = negativeText,
        onError = onAccent,
        errorContainer = negativeSurface,
        onErrorContainer = negativeText,
    )
