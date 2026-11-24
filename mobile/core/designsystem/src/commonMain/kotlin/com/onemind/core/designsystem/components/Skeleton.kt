package com.onemind.core.designsystem.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindMotion
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * A loading placeholder block (`chartMuted` with a shimmer sweeping across it) in the slot of a value
 * that hasn't arrived, so a Loading state has the loaded layout and nothing moves when the figures come
 * (never a `0`, rule 2). Sized by [modifier]; decorative: the screen's container says *Loading …* to
 * screen readers.
 *
 * Every block on screen shows the same sweep: its phase comes from the frame clock and its band is placed
 * in window coordinates, so the highlight runs across the whole screen like one light, whenever a block
 * was composed. It only redraws (no recomposition per frame).
 */
@Composable
fun OneMindSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(OneMindTheme.radius.tag),
) {
    val progress = shimmerProgress()
    val base = OneMindTheme.colors.chartMuted
    val highlight = lerp(base, OneMindTheme.colors.surface, SHIMMER_HIGHLIGHT_FRACTION)
    val windowWidth =
        LocalWindowInfo.current.containerSize.width
            .toFloat()
    val left = remember { mutableFloatStateOf(0f) }
    Box(
        modifier =
            modifier
                .onGloballyPositioned { left.floatValue = it.positionInWindow().x }
                .drawBehind {
                    val band = SHIMMER_BAND.toPx()
                    // The band's centre travels from just left of the window to just right of it.
                    val centre = -band + (windowWidth + 2 * band) * progress.floatValue - left.floatValue
                    val brush = Brush.linearGradient(listOf(base, highlight, base), Offset(centre - band, 0f), Offset(centre + band, 0f))
                    drawOutline(shape.createOutline(size, layoutDirection, this), brush)
                },
    )
}

/** The shimmer's phase (0–1) from the frame clock, so every block on screen is in step. */
@Composable
private fun shimmerProgress(): MutableFloatState {
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                progress.floatValue = (frame % OneMindMotion.SHIMMER_MILLIS).toFloat() / OneMindMotion.SHIMMER_MILLIS
            }
        }
    }
    return progress
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

/** How far the highlight goes from `chartMuted` towards `surface`. */
private const val SHIMMER_HIGHLIGHT_FRACTION = 0.6f

/** Half the width of the highlight band. */
private val SHIMMER_BAND = 120.dp
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
