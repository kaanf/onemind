package com.onemind.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and sizes from `docs/design/DESIGN_SYSTEM.md`. */
@Immutable
data class OneMindSpacing(
    val screenHorizontal: Dp = 20.dp,
    /** Top and bottom padding of full-screen flows without a tab bar (sign-in). */
    val screenTop: Dp = 48.dp,
    val screenBottom: Dp = 32.dp,
    /** Between the blocks of a form screen (header, inputs, actions). */
    val blockGap: Dp = 28.dp,
    /** Between a title and its supporting text. */
    val textGap: Dp = 8.dp,
    /** Height of a top bar with a back button. */
    val topBarHeight: Dp = 56.dp,
    val sectionGap: Dp = 16.dp,
    val sectionGapTight: Dp = 14.dp,
    val rowGap: Dp = 12.dp,
    val cardPadding: Dp = 20.dp,
    val cardPaddingCompact: Dp = 16.dp,
    val tileGap: Dp = 8.dp,
    /** Minimum touch target on both axes. */
    val minTouchTarget: Dp = 44.dp,
    val borderWidth: Dp = 1.dp,
    /** Focused input and composer border. */
    val focusBorderWidth: Dp = 1.5.dp,
)
