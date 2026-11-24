package com.onemind.sales.presentation.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.onemind.core.designsystem.components.OneMindSkeleton
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
 * A text line's skeleton: as tall as a line of [style] (so nothing moves when the text arrives), the bar
 * itself as tall as the font, [widthFraction] of the width.
 */
@Composable
internal fun SkeletonLine(
    style: TextStyle,
    widthFraction: Float,
) {
    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth().height(style.lineHeightDp())) {
        SkeletonBlock(Modifier.fillMaxWidth(widthFraction).height(style.fontSizeDp()))
    }
}

@Composable
internal fun TextStyle.lineHeightDp(): Dp =
    with(LocalDensity.current) {
        val height = if (lineHeight.isEm) TextUnit(fontSize.value * lineHeight.value, TextUnitType.Sp) else lineHeight
        height.toDp()
    }

@Composable
private fun TextStyle.fontSizeDp(): Dp = with(LocalDensity.current) { fontSize.toDp() }
