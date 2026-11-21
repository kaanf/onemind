package com.onemind.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

// Sample text for the token preview only; never shown in the app.
@Preview
@Composable
private fun ThemePreview() {
    OneMindTheme {
        val colors = OneMindTheme.colors
        val type = OneMindTheme.typography
        Column(
            modifier =
                Modifier
                    .background(colors.background)
                    .padding(OneMindTheme.spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.rowGap),
        ) {
            Text("Today", style = type.title, color = colors.textPrimary)
            Text("PAID SO FAR", style = type.overline, color = colors.textSecondary)
            Text("48 320 Kč", style = type.displayLarge, color = colors.textPrimary)
            Text("Products by hour", style = type.headline, color = colors.textPrimary)
            Text("Pilsner Urquell 0.5 · 87 sold", style = type.body, color = colors.textPrimary)
            Text("Friday, 6 November · 21:40", style = type.bodySmall, color = colors.textSecondary)
            Text("Bar lead · since 16:00", style = type.caption, color = colors.textSecondary)
            Swatches(listOf(colors.accent, colors.positive, colors.negative, colors.warningText, colors.selection))
            Swatches(colors.heatRamp)
        }
    }
}

@Composable
private fun Swatches(swatches: List<Color>) {
    Row(horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
        swatches.forEach { swatch ->
            val shape = RoundedCornerShape(OneMindTheme.radius.heatCell)
            Column(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(swatch, shape)
                        .border(OneMindTheme.spacing.borderWidth, OneMindTheme.colors.border, shape),
            ) {}
        }
    }
}
