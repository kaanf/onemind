package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/** Initials avatar for staff rows (*TN*): 40 × 40, radius 12, `surfaceMuted`. Initials come from the caller. */
@Composable
fun AvatarInitials(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(40.dp)
                .background(OneMindTheme.colors.surfaceMuted, RoundedCornerShape(OneMindTheme.radius.control)),
    ) {
        Text(
            text = initials,
            style = OneMindTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = OneMindTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}
