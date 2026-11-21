package com.onemind.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * Detail screen top bar: 56 high, 44 × 44 back button, title 17/600 and optional subtitle 12.
 * [backContentDescription] names the destination (*Back to Sales*).
 */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = OneMindTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(OneMindTheme.spacing.minTouchTarget)) {
            Icon(
                OneMindIcons.Back,
                contentDescription = backContentDescription,
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column {
            Text(text = title, style = OneMindTheme.typography.headline, color = colors.textPrimary, maxLines = 1)
            if (subtitle != null) {
                Text(text = subtitle, style = OneMindTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}
