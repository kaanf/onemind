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
    /** Leading icon box in a list row (*Possible loss* on Today) and the icon inside it. */
    val iconBoxSize: Dp = 32.dp,
    val iconBoxIconSize: Dp = 18.dp,
    /** Side padding of the venue pill (*Main bar ▾*) and its chevron. */
    val pillPadding: Dp = 14.dp,
    val chevronSize: Dp = 12.dp,
    /** Status dot in the venue pill. */
    val statusDot: Dp = 8.dp,
    /** Hourly bars with value labels on top (02a); Today's chart is 52. */
    val chartHeightTall: Dp = 88.dp,
    /** Bar gap of the 02a chart (BarChart's default is 4). */
    val barGapWide: Dp = 5.dp,
    /** Legend swatch (02c, 10×10) and the small one beside *Card* / *Cash* (02, 8×8). */
    val swatch: Dp = 10.dp,
    val swatchSmall: Dp = 8.dp,
    /** Stacked share bar (02 card/cash, 02c by type): height and gap between segments. */
    val shareBarHeight: Dp = 10.dp,
    val shareBarGap: Dp = 3.dp,
    /** Marker beside the selected row (02a). */
    val selectionMarkerWidth: Dp = 4.dp,
    val selectionMarkerHeight: Dp = 28.dp,
    /** Taller list rows (02c by type) and shorter link rows (02 *Products by hour*). */
    val rowHeightTall: Dp = 60.dp,
    val rowHeightLink: Dp = 54.dp,
    /** Default list row height (02a item rows draw their own row at this height). */
    val listRowHeight: Dp = 56.dp,
    /** Card padding with a shorter top and bottom: 18 (02 *By payment*, 02a chart card), sides stay 20. */
    val cardPaddingTight: Dp = 18.dp,
    /** Hero column gap: overline, figure, badge (02c). */
    val heroGap: Dp = 6.dp,
    /** Chart header: overline over *n sold* (02a). */
    val listGap: Dp = 4.dp,
    /** Name over meta in list rows. */
    val hairlineGap: Dp = 2.dp,
)
