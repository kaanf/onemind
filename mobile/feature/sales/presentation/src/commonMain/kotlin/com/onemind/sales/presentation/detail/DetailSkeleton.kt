package com.onemind.sales.presentation.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.onemind.core.designsystem.components.OneMindSkeleton
import com.onemind.core.designsystem.components.OneMindSkeletonText
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * One skeleton block of the loading layout (no figure, no 0: rule 2), the shared shimmering
 * [OneMindSkeleton]; sized by the caller, mostly as shares of a slot.
 */
@Composable
internal fun SkeletonBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(OneMindTheme.radius.tag),
) {
    OneMindSkeleton(modifier = modifier, shape = shape)
}

/**
 * A text line's skeleton: exactly as tall as a line of [style] (so nothing moves when the text arrives),
 * [widthFraction] of the width.
 */
@Composable
internal fun SkeletonLine(
    style: TextStyle,
    widthFraction: Float,
) {
    OneMindSkeletonText(style, widthFraction = widthFraction)
}

@Composable
internal fun TextStyle.lineHeightDp(): Dp =
    with(LocalDensity.current) {
        val height = if (lineHeight.isEm) TextUnit(fontSize.value * lineHeight.value, TextUnitType.Sp) else lineHeight
        height.toDp()
    }
