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
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindTheme

/** One bar; [color] comes from the screen's highlight rule (accent, `chartMuted`, `chartFuture`, …). */
data class Bar(
    val value: Float,
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
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val labelColor = OneMindTheme.colors.textSecondary
    val labelSpace = if (bars.any { it.valueLabel != null }) 16.dp else 0.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription },
    ) {
        bars.forEach { bar ->
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val barHeight = (maxHeight - labelSpace) * barFraction(bar.value, max)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (bar.valueLabel != null) {
                        Text(
                            text = bar.valueLabel,
                            style = OneMindTheme.typography.caption.copy(fontSize = 10.sp),
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

/** Bar height as a share of the tallest bar, clamped to 0..1 (negative values draw as empty). */
internal fun barFraction(
    value: Float,
    max: Float,
): Float = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
