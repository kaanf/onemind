package com.onemind.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii from `docs/design/DESIGN_SYSTEM.md`. */
@Immutable
data class OneMindRadius(
    val card: Dp = 16.dp,
    val panel: Dp = 14.dp,
    /** Buttons and inputs. */
    val control: Dp = 12.dp,
    /** Filter chips, outline buttons in headers, table tiles. */
    val chip: Dp = 10.dp,
    /** Leading icon boxes in rows. */
    val iconBox: Dp = 8.dp,
    val checkbox: Dp = 7.dp,
    /** Delta badges and small tags. */
    val tag: Dp = 6.dp,
    val heatCell: Dp = 5.dp,
    val bar: Dp = 3.dp,
    /** Legend swatches: 10×10 (02c) and 8×8 (02). */
    val swatch: Dp = 3.dp,
    val swatchSmall: Dp = 2.dp,
    /** Stacked share bar segments. */
    val shareBar: Dp = 5.dp,
    /** Selected-row marker (02a). */
    val marker: Dp = 2.dp,
)
