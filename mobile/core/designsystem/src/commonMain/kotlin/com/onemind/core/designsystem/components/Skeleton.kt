package com.onemind.core.designsystem.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindMotion
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * A loading placeholder block (`chartMuted`, pulsing) in the slot of a value that hasn't arrived, so a
 * Loading state has the loaded layout and nothing moves when the figures come (never a `0`, rule 2).
 * Sized by [modifier]; decorative: the screen's container says *Loading …* to screen readers.
 */
@Composable
fun OneMindSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(OneMindTheme.radius.tag),
) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(OneMindMotion.PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }.background(OneMindTheme.colors.chartMuted, shape))
}

/**
 * A skeleton one text line of [style] high (the block is ~70% of the line, centred), [width] wide: the
 * value slot keeps the loaded text's height.
 */
@Composable
fun OneMindSkeletonText(
    style: TextStyle,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    // Type styles set the line height in em (Type.kt), so it is a multiple of the font size.
    val line = style.lineHeight
    val lineSp =
        when {
            line.isSp -> line
            line.isEm -> (style.fontSize.value * line.value).sp
            else -> style.fontSize
        }
    val lineHeight = with(LocalDensity.current) { lineSp.toDp() }
    Box(contentAlignment = Alignment.CenterStart, modifier = modifier.height(lineHeight)) {
        OneMindSkeleton(modifier = Modifier.width(width).height(lineHeight * TEXT_BLOCK_FRACTION))
    }
}

private const val PULSE_MIN_ALPHA = 0.55f
private const val TEXT_BLOCK_FRACTION = 0.7f

/**
 * A value slot that shows [skeleton] until [value] arrives, then crossfades to [content] in place
 * (medium duration). The slot keeps its size, and numbers are never animated through invented values.
 */
@Composable
fun <T : Any> OneMindLoadingSlot(
    value: T?,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Crossfade(targetState = value, animationSpec = OneMindMotion.medium(), modifier = modifier, label = "loadingSlot") { current ->
        if (current == null) skeleton() else content(current)
    }
}
