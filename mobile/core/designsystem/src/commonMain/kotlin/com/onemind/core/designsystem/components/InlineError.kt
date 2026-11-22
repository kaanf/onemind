package com.onemind.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.onemind.core.designsystem.theme.OneMindTheme

/** An error line under a field or form (`negativeText`, 13); announced by screen readers when it appears. */
@Composable
fun InlineError(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = OneMindTheme.typography.bodySmall,
        color = OneMindTheme.colors.negativeText,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
