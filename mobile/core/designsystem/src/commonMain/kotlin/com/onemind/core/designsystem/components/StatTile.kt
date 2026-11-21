package com.onemind.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindTheme

enum class StatTileStyle {
    /** Caption label + 16/600 value, inside a card (*Guests 286*). */
    Compact,

    /** Overline label + Inter Tight figure (*HOURS 18:40*). */
    Overline,
}

/** Label + value, laid out by the caller in a grid of 2–3 columns. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: StatTileStyle = StatTileStyle.Compact,
    valueColor: Color = OneMindTheme.colors.textPrimary,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (style == StatTileStyle.Overline) 4.dp else 2.dp),
    ) {
        when (style) {
            StatTileStyle.Compact -> {
                Text(text = label, style = type.caption, color = colors.textSecondary, maxLines = 1)
                Text(
                    text = value,
                    style = type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = valueColor,
                    maxLines = 1,
                )
            }

            StatTileStyle.Overline -> {
                Text(text = label.uppercase(), style = type.overline, color = colors.textSecondary, maxLines = 1)
                Text(text = value, style = type.figure, color = valueColor, maxLines = 1)
            }
        }
    }
}
