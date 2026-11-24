package com.onemind.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme

/**
 * Detail screen top bar: 56 high, 44 × 44 back button, title 17/600 and optional subtitle 12.
 * [backContentDescription] names the destination (*Back to Sales*). [subtitleLoading] shimmers the
 * subtitle's line while its value loads, so the title doesn't move when it arrives.
 */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleLoading: Boolean = false,
) {
    val colors = OneMindTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
    ) {
        // A 44 circle with a ripple that fills it (an IconButton's state layer is 40).
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(OneMindTheme.spacing.minTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backContentDescription },
        ) {
            Icon(OneMindIcons.Back, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(text = title, style = OneMindTheme.typography.headline, color = colors.textPrimary, maxLines = 1)
            if (subtitleLoading) {
                OneMindSkeletonText(OneMindTheme.typography.caption, width = SUBTITLE_SKELETON)
            } else if (subtitle != null) {
                Text(text = subtitle, style = OneMindTheme.typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** About a date range's width (*2 to 8 Nov*). */
private val SUBTITLE_SKELETON = 72.dp
