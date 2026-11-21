package com.onemind.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens from `docs/design/DESIGN_SYSTEM.md`. Screens use these, never literal colors.
 * Only a light palette is designed (dark mode: open question P7).
 */
@Immutable
data class OneMindColors(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val iconMuted: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val positive: Color,
    val positiveSurface: Color,
    val negative: Color,
    val negativeText: Color,
    val negativeSurface: Color,
    val warningText: Color,
    val warningStrongText: Color,
    val warningSurface: Color,
    val warningBorder: Color,
    val selection: Color,
    val chartMuted: Color,
    val chartMutedStrong: Color,
    val chartFuture: Color,
    /** Hourly patterns heatmap, 7 steps from quiet to busiest. */
    val heatRamp: List<Color>,
    /** Tables occupancy, 6 steps; text on the last two steps is [onAccent]. */
    val tablesRamp: List<Color>,
)

val LightColors =
    OneMindColors(
        background = Color(0xFFF7F7F5),
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = Color(0xFFFAFAF8),
        surfaceMuted = Color(0xFFF1F1EE),
        border = Color(0xFFE7E7E3),
        borderStrong = Color(0xFFE2E2DE),
        divider = Color(0xFFEFEFEC),
        textPrimary = Color(0xFF14161A),
        // Canvas #6B7079, darkened for 4.5:1 on surfaceMuted (WCAG AA).
        textSecondary = Color(0xFF696E77),
        textTertiary = Color(0xFF5C626C),
        textPlaceholder = Color(0xFF8A8F98),
        iconMuted = Color(0xFF9BA0A8),
        accent = Color(0xFF4F46E5),
        accentPressed = Color(0xFF3730A3),
        accentSoft = Color(0xFFEEF0FD),
        onAccent = Color(0xFFFFFFFF),
        positive = Color(0xFF15803D),
        positiveSurface = Color(0xFFECFDF3),
        negative = Color(0xFFDC2626),
        negativeText = Color(0xFFB91C1C),
        negativeSurface = Color(0xFFFEF2F2),
        warningText = Color(0xFFB45309),
        warningStrongText = Color(0xFF92400E),
        warningSurface = Color(0xFFFFFBEB),
        warningBorder = Color(0xFFFDE68A),
        selection = Color(0xFFF59E0B),
        chartMuted = Color(0xFFD5D7DB),
        chartMutedStrong = Color(0xFFC9CCD1),
        chartFuture = Color(0xFFEEEFF1),
        heatRamp =
            listOf(
                Color(0xFFF1F1EE),
                Color(0xFFE0E2F8),
                Color(0xFFC4C8F3),
                Color(0xFF9AA0EC),
                Color(0xFF6B71E3),
                Color(0xFF4338CA),
                Color(0xFF312E81),
            ),
        tablesRamp =
            listOf(
                Color(0xFFF1F1EE),
                Color(0xFFE0E2F8),
                Color(0xFFC4C8F3),
                Color(0xFF9AA0EC),
                // Canvas #6B71E3, darkened for 4.5:1 with white text (WCAG AA).
                Color(0xFF6269E1),
                Color(0xFF4F46E5),
            ),
    )
