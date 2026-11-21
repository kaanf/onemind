package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.core.designsystem.theme.OneMindColors
import com.onemind.core.designsystem.theme.OneMindTheme

/** One cell: [level] is a step of the heat ramp (0 = quiet); [label] is spoken (*Sat 22:00, 54 guests*). */
data class HeatCell(
    val level: Int,
    val label: String,
)

data class HeatmapRow(
    val label: String,
    val cells: List<HeatCell>,
)

/**
 * Day × hour grid of selectable cells (28 high, radius 5) coloured by the heat ramp; the selected cell
 * has a 2 px `selection` outline. [selected] is (row, column).
 */
@Composable
fun Heatmap(
    columnLabels: List<String>,
    rows: List<HeatmapRow>,
    selected: Pair<Int, Int>?,
    onSelect: (row: Int, column: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OneMindTheme.colors
    val labelStyle = OneMindTheme.typography.caption
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 34.dp, bottom = 2.dp)) {
            columnLabels.forEach { label ->
                Text(
                    text = label,
                    style = labelStyle.copy(fontSize = 10.sp),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = labelStyle.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                    modifier = Modifier.width(30.dp),
                )
                row.cells.forEachIndexed { columnIndex, cell ->
                    val isSelected = selected == rowIndex to columnIndex
                    val shape = RoundedCornerShape(OneMindTheme.radius.heatCell)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(28.dp)
                                .background(colors.heatColor(cell.level), shape)
                                .border(2.dp, if (isSelected) colors.selection else Color.Transparent, shape)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Button,
                                    onClick = { onSelect(rowIndex, columnIndex) },
                                ).semantics { contentDescription = cell.label },
                    )
                }
            }
        }
    }
}

/** Heat ramp colour for [level], clamped to the ramp. */
fun OneMindColors.heatColor(level: Int): Color = heatRamp[level.coerceIn(0, heatRamp.lastIndex)]
