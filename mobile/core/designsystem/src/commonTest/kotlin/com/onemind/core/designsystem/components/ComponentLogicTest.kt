package com.onemind.core.designsystem.components

import com.onemind.core.designsystem.theme.LightColors
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentLogicTest {
    private val colors = LightColors

    @Test
    fun otpKeepsDigitsOnlyUpToLength() {
        assertEquals("482913", sanitizeOtp("482 913", 6))
        assertEquals("482913", sanitizeOtp("482-9137", 6))
        assertEquals("", sanitizeOtp("abc", 6))
    }

    @Test
    fun barFractionIsRelativeToTallestAndClamped() {
        assertEquals(0.5f, barFraction(45f, 90f))
        assertEquals(1f, barFraction(90f, 90f))
        assertEquals(0f, barFraction(-5f, 90f))
        assertEquals(0f, barFraction(10f, 0f))
    }

    @Test
    fun aBarWithoutAValueIsAnEmptySlotNotAZero() {
        val muted = LightColors.chartMuted
        val bars = listOf(Bar(45f, muted), Bar(90f, muted), Bar(0f, muted), Bar(null, muted))

        // 0 still draws (the minimum stub); null draws nothing and doesn't count towards the tallest.
        assertEquals(listOf(0.5f, 1f, 0f, null), drawnFractions(bars))
        assertEquals(listOf(null, null), drawnFractions(listOf(Bar(null, muted), Bar(null, muted))))
    }

    @Test
    fun floorPlanPutsSpanningTileOnItsOwnRow() {
        val bar = FloorTile("Bar", "2 420", TileTone.Busy, "Bar", spanRow = true)
        val table = FloorTile("T1", "Free", TileTone.Free, "T1")
        val tiles = listOf(bar) + List(6) { table }

        assertEquals(listOf(listOf(0), listOf(1, 2, 3, 4), listOf(5, 6)), floorPlanRows(tiles, columns = 4))
    }

    @Test
    fun tileTextTurnsWhiteFromRampStepFive() {
        assertEquals(colors.textPrimary, colors.tileColors(TileTone.Ramp(3)).second)
        assertEquals(colors.onAccent, colors.tileColors(TileTone.Ramp(4)).second)
        assertEquals(colors.tablesRamp.last(), colors.tileColors(TileTone.Ramp(99)).first)
        assertEquals(colors.surfaceMuted to colors.textSecondary, colors.tileColors(TileTone.Free))
    }

    @Test
    fun heatColorIsClampedToTheRamp() {
        assertEquals(colors.heatRamp.first(), colors.heatColor(-1))
        assertEquals(colors.heatRamp[3], colors.heatColor(3))
        assertEquals(colors.heatRamp.last(), colors.heatColor(42))
    }

    @Test
    fun badgeTonesUseTintedSurfaces() {
        assertEquals(colors.positiveSurface to colors.positive, colors.badgeColors(BadgeTone.Positive))
        assertEquals(colors.negativeSurface to colors.negativeText, colors.badgeColors(BadgeTone.Negative))
    }
}
