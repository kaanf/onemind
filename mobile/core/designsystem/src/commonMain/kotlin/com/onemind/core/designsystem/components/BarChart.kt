package com.onemind.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * One bar; [color] comes from the screen's highlight rule (accent, `chartMuted`, `chartFuture`, …).
 * [value] only sets the bar's height relative to the other bars (drawing, so `Float` is fine); never
 * pass money through it for display. The amount or count a user reads is [valueLabel], formatted
 * exactly by the caller. A `null` value is an empty slot: nothing is drawn there, not even the minimum
 * stub of a 0 (rule 2: a missing figure never looks like a 0).
 */
data class Bar(
    val value: Float?,
    val color: Color,
    val valueLabel: String? = null,
)

/**
 * Rounded bars (3 px) scaled to the largest value, optional value labels above. One [Canvas]: no layout
 * node per bar, the labels measured once per bar list (remembered) and drawn with [drawText], so a change
 * of bars redraws without re-laying out, and a frame allocates nothing. The chart is one accessibility
 * node: [contentDescription] says what it shows (*Revenue by hour, peak 22:00*).
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    gap: Dp = 4.dp,
) {
    val fractions = remember(bars) { drawnFractions(bars) }
    val labelColor = OneMindTheme.colors.textSecondary
    val labelStyle = OneMindTheme.typography.chartLabel
    val measurer = rememberTextMeasurer()
    val labels =
        remember(bars, labelStyle, measurer) {
            bars.map { bar -> bar.valueLabel?.let { measurer.measure(it, labelStyle, maxLines = 1) } }
        }
    val hasLabels = labels.any { it != null }
    val radius = OneMindTheme.radius.bar
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { this.contentDescription = contentDescription },
    ) {
        if (bars.isEmpty()) return@Canvas
        val gapPx = gap.toPx()
        val barWidth = (size.width - gapPx * (bars.size - 1)) / bars.size
        val labelSpace = if (hasLabels) LABEL_SPACE.toPx() else 0f
        val minBar = MIN_BAR.toPx()
        val labelGap = LABEL_GAP.toPx()
        val corner = CornerRadius(radius.toPx())
        bars.forEachIndexed { index, bar ->
            // null: an empty slot, nothing drawn, not even the minimum stub of a 0 (rule 2).
            val fraction = fractions[index] ?: return@forEachIndexed
            val barHeight = maxOf((size.height - labelSpace) * fraction, minBar)
            val left = index * (barWidth + gapPx)
            val top = size.height - barHeight
            drawRoundRect(color = bar.color, topLeft = Offset(left, top), size = Size(barWidth, barHeight), cornerRadius = corner)
            labels[index]?.let { label ->
                drawText(
                    textLayoutResult = label,
                    color = labelColor,
                    topLeft = Offset(left + (barWidth - label.size.width) / 2f, top - labelGap - label.size.height),
                )
            }
        }
    }
}

/** Room above the bars for the value labels, when there are any. */
private val LABEL_SPACE = 16.dp

/** Between a label and its bar. */
private val LABEL_GAP = 4.dp

/** A 0 still shows as a stub (an empty slot is `null`, not 0). */
private val MIN_BAR = 2.dp

/**
 * What [BarChart] draws for each bar: its height as a share of the tallest, or `null` for an empty slot
 * (a `null` value: no bar, no stub, no label).
 */
internal fun drawnFractions(bars: List<Bar>): List<Float?> {
    val max = bars.mapNotNull { it.value }.maxOrNull()?.takeIf { it > 0f } ?: 1f
    return bars.map { bar -> bar.value?.let { barFraction(it, max) } }
}

/** Bar height as a share of the tallest bar, clamped to 0..1 (negative values draw as empty). */
internal fun barFraction(
    value: Float,
    max: Float,
): Float = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
