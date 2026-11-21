package com.onemind.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

sealed interface ProgressStatus {
    data object Done : ProgressStatus

    /** [fraction] in 0..1, or null when the amount is unknown (no bar). */
    data class Running(
        val fraction: Float?,
    ) : ProgressStatus

    data object Waiting : ProgressStatus
}

/** Import/sync step: status icon (done ✓ green, running ring accent, waiting dashed), name, meta, optional 4 px bar. */
@Composable
fun ProgressRow(
    name: String,
    meta: String,
    status: ProgressStatus,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = OneMindTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.rowGap),
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
        ) {
            StatusIcon(status)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = name,
                        style = OneMindTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = meta, style = OneMindTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1)
                }
                if (status is ProgressStatus.Running && status.fraction != null) {
                    val shape = RoundedCornerShape(2.dp)
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(colors.divider, shape)) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(status.fraction.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(colors.accent, shape),
                        )
                    }
                }
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(OneMindTheme.spacing.borderWidth).background(colors.divider))
        }
    }
}

@Composable
private fun StatusIcon(status: ProgressStatus) {
    val colors = OneMindTheme.colors
    val size = Modifier.size(24.dp)
    when (status) {
        ProgressStatus.Done -> {
            Box(contentAlignment = Alignment.Center, modifier = size.background(colors.positive, CircleShape)) {
                Icon(OneMindIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(12.dp))
            }
        }

        is ProgressStatus.Running -> {
            Canvas(modifier = size) {
                val width = 2.5.dp.toPx()
                drawCircle(colors.borderStrong, radius = (this.size.minDimension - width) / 2, style = Stroke(width))
                drawArc(
                    color = colors.accent,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(this.size.width - width, this.size.height - width),
                    style = Stroke(width),
                )
            }
        }

        ProgressStatus.Waiting -> {
            Canvas(modifier = size) {
                val width = 1.5.dp.toPx()
                drawCircle(
                    colors.iconMuted,
                    radius = (this.size.minDimension - width) / 2,
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            }
        }
    }
}
