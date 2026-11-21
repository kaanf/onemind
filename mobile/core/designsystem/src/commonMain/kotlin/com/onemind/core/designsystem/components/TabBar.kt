package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindTheme

data class TabBarItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * Bottom tab bar: icon 22 + label 11; the active tab is `textPrimary` with a 28 × 3 accent bar on the
 * top edge. White background, top border, bottom inset for the home indicator / gesture bar.
 */
@Composable
fun TabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        Box(modifier = Modifier.fillMaxWidth().height(OneMindTheme.spacing.borderWidth).background(colors.border))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val contentColor = if (selected) colors.textPrimary else colors.textSecondary
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                ) {
                    if (selected) {
                        Box(
                            modifier =
                                Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .background(colors.accent, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    ) {
                        Icon(item.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
                        Text(
                            text = item.label,
                            style =
                                OneMindTheme.typography.tabLabel.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
