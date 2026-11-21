package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindTheme

/** Text tabs with a 2 px accent underline on the selected one (*Hour / Day / Week / Month*). */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(OneMindTheme.spacing.borderWidth)
                    .background(colors.border),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(OneMindTheme.spacing.minTouchTarget - 2.dp),
                    ) {
                        Text(
                            text = label,
                            style =
                                OneMindTheme.typography.body.copy(
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}
