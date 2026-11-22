package com.onemind.core.designsystem.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    fun tappingAFilledOtpBoxSelectsItsDigitAndAnEmptyBoxGoesToTheEnd() {
        assertEquals(TextRange(1, 2), otpSelectionForTap(1, "4829"))
        assertEquals(TextRange(0, 1), otpSelectionForTap(0, "482913"))
        assertEquals(TextRange(4), otpSelectionForTap(5, "4829"))
        assertEquals(TextRange(0), otpSelectionForTap(3, ""))
    }

    @Test
    fun theActiveOtpBoxFollowsTheCursor() {
        // Typing at the end: the next empty box; a full code keeps the last box.
        assertEquals(4, otpActiveBox("4829", TextRange(4), 6))
        assertEquals(5, otpActiveBox("482913", TextRange(6), 6))
        // A tapped digit is the active box until it is replaced.
        assertEquals(1, otpActiveBox("482913", TextRange(1, 2), 6))
    }

    @Test
    fun twoDigitsCanBeCorrectedInARow() {
        // 482913: tap box 2 (selects "8"), type 7, then 5. The IME replaces the selection each time.
        var field = TextFieldValue("482913", otpSelectionForTap(1, "482913"))
        field = otpEdit(TextFieldValue("472913", TextRange(2)), 6)
        assertEquals(TextFieldValue("472913", TextRange(2, 3)), field)
        field = otpEdit(TextFieldValue("475913", TextRange(3)), 6)
        assertEquals("475913", field.text)
        assertEquals(TextRange(3, 4), field.selection)
    }

    @Test
    fun anOtpEditKeepsDigitsAndTheCursorInside() {
        // Backspace on a selected digit: not full any more, plain cursor.
        assertEquals(TextFieldValue("42913", TextRange(1)), otpEdit(TextFieldValue("42913", TextRange(1)), 6))
        // Typing at the end of a full code: the extra digit is dropped, the cursor stays at the end.
        assertEquals(TextFieldValue("482913", TextRange(6)), otpEdit(TextFieldValue("4829134", TextRange(7)), 6))
        // A paste with separators.
        assertEquals(TextFieldValue("4829", TextRange(4)), otpEdit(TextFieldValue("48-29", TextRange(5)), 6))
    }

    @Test
    fun aValueSetFromOutsideMovesTheCursorToItsEnd() {
        val own = TextFieldValue("482913", TextRange(1, 2))
        assertEquals(TextRange(1, 2), otpSelection("482913", own))
        // Cleared after a wrong code, or filled by the screen.
        assertEquals(TextRange(0), otpSelection("", own))
        assertEquals(TextRange(3), otpSelection("123", own))
    }

    @Test
    fun barFractionIsRelativeToTallestAndClamped() {
        assertEquals(0.5f, barFraction(45f, 90f))
        assertEquals(1f, barFraction(90f, 90f))
        assertEquals(0f, barFraction(-5f, 90f))
        assertEquals(0f, barFraction(10f, 0f))
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
