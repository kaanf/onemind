package com.onemind.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** Text tokens keep WCAG 4.5:1 on the surfaces they are used on (`DESIGN_SYSTEM.md` › Accessibility). */
class ColorContrastTest {
    private val c = LightColors

    @Test
    fun textTokensOnBackgroundAndSurface() {
        val texts =
            mapOf(
                "textPrimary" to c.textPrimary,
                "textSecondary" to c.textSecondary,
                "textTertiary" to c.textTertiary,
                "accent" to c.accent,
                "negativeText" to c.negativeText,
                "warningText" to c.warningText,
                "positive" to c.positive,
            )
        for ((ground, color) in mapOf("background" to c.background, "surface" to c.surface)) {
            texts.forEach { (name, text) -> assertReadable(text, color, "$name on $ground") }
        }
    }

    @Test
    fun textOnTintedSurfaces() {
        assertReadable(c.onAccent, c.accent, "onAccent on accent")
        assertReadable(c.onAccent, c.textPrimary, "onAccent on textPrimary (dark button, selected chip)")
        assertReadable(c.textPrimary, c.accentSoft, "textPrimary on accentSoft (hint card)")
        assertReadable(c.positive, c.positiveSurface, "positive on positiveSurface (delta badge)")
        assertReadable(c.negativeText, c.negativeSurface, "negativeText on negativeSurface")
        assertReadable(c.warningStrongText, c.warningSurface, "warningStrongText on warningSurface")
        assertReadable(c.onAccent, c.tablesRamp.last(), "onAccent on the busiest table tile")
        assertReadable(c.textSecondary, c.surfaceMuted, "textSecondary on surfaceMuted (muted avatar, free tile)")
        c.tablesRamp.drop(4).forEach { step -> assertReadable(c.onAccent, step, "onAccent on tables ramp step from 5") }
    }

    private fun assertReadable(
        text: Color,
        ground: Color,
        label: String,
    ) {
        val ratio = contrast(text, ground)
        assertTrue(ratio >= 4.5, "$label: contrast $ratio < 4.5")
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
