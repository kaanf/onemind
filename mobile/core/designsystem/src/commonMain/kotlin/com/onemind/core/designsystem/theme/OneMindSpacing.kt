package com.onemind.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and sizes from `docs/design/DESIGN_SYSTEM.md`. */
@Immutable
data class OneMindSpacing(
    val screenHorizontal: Dp = 20.dp,
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
