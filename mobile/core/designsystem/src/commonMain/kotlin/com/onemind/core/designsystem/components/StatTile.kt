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

    /** Caption label + 18/600 value, in a live-figure tile (*Guests 286* on Today). */
    Tile,
}

/**
 * Label + value, laid out by the caller in a grid of 2–3 columns. A `null` [value] (loading) shimmers in
 * the value's line, exactly as tall as the value, and crossfades to it in place when it arrives.
 */
@Composable
fun StatTile(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    style: StatTileStyle = StatTileStyle.Compact,
    valueColor: Color = OneMindTheme.colors.textPrimary,
) {
    val colors = OneMindTheme.colors
    val type = OneMindTheme.typography
    val (labelText, labelStyle, valueStyle) =
        when (style) {
            StatTileStyle.Compact -> Triple(label, type.caption, type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            StatTileStyle.Tile -> Triple(label, type.caption, type.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            StatTileStyle.Overline -> Triple(label.uppercase(), type.overline, type.figure)
        }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (style == StatTileStyle.Compact) 2.dp else 4.dp),
    ) {
        Text(text = labelText, style = labelStyle, color = colors.textSecondary, maxLines = 1)
        OneMindLoadingSlot(value = value, skeleton = { OneMindSkeletonText(valueStyle, width = VALUE_SKELETON) }) {
            Text(text = it, style = valueStyle, color = valueColor, maxLines = 1)
        }
    }
}

/** About a short figure's width (*1 138 Kč*). */
private val VALUE_SKELETON = 72.dp
