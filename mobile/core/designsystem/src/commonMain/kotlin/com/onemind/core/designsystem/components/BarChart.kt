package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Rounded bars (3 px) scaled to the largest value, optional value labels above. The chart is one
 * accessibility node: [contentDescription] says what it shows (*Revenue by hour, peak 22:00*).
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    gap: Dp = 4.dp,
) {
    val fractions = drawnFractions(bars)
    val labelColor = OneMindTheme.colors.textSecondary
    val labelSpace = if (bars.any { it.valueLabel != null }) 16.dp else 0.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription },
    ) {
        bars.forEachIndexed { index, bar ->
            val fraction = fractions[index]
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (fraction == null) return@BoxWithConstraints
                val barHeight = (maxHeight - labelSpace) * fraction
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (bar.valueLabel != null) {
                        Text(
                            text = bar.valueLabel,
                            style = OneMindTheme.typography.chartLabel,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight.coerceAtLeast(2.dp))
                                .background(bar.color, RoundedCornerShape(OneMindTheme.radius.bar)),
                    )
                }
            }
        }
    }
}

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
