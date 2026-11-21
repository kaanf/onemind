package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

/** Filter chip: 36 high, radius 10. Selected: dark fill and white text; unselected: white with border. */
@Composable
fun OneMindFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    val shape = RoundedCornerShape(OneMindTheme.radius.chip)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = 36.dp)
                .background(if (selected) colors.textPrimary else colors.surface, shape)
                .border(
                    OneMindTheme.spacing.borderWidth,
                    if (selected) colors.textPrimary else colors.borderStrong,
                    shape,
                ).selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp),
    ) {
        Text(
            text = label,
            style =
                OneMindTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            color = if (selected) colors.onAccent else colors.textPrimary,
            maxLines = 1,
        )
    }
}
