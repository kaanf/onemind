package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * Stand-in for a tab whose screen is not built yet: the tab title and one line of text, no figures.
 * Replace it with the real screen; don't use it for empty data states (those need designs, P7).
 */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val spacing = OneMindTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier =
            modifier
                .fillMaxSize()
                .background(OneMindTheme.colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sectionGap),
    ) {
        Text(
            text = title,
            style = OneMindTheme.typography.title,
            color = OneMindTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = OneMindTheme.typography.body,
            color = OneMindTheme.colors.textSecondary,
        )
    }
}
