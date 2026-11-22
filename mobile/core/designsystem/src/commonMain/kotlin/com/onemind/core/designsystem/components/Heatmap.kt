package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * has a 2 px `selection` outline. [selected] is (row, column). Every row has the same number of cells.
 *
 * Touch target exception (DESIGN_SYSTEM.md): with 10 hour columns (17:00 to 02:00, canvas 02b) a cell is about 29 × 28 dp, so 44 × 44
 * isn't possible. Cells stay at least 24 × 24 (WCAG 2.5.8 AA) and one tap handler on the whole cell area
 * selects the nearest cell, so there are no dead zones between cells; a drag (vertical scroll) is not a
 * tap and passes through. Each cell is still its own accessibility node with its spoken label.
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
    val columns = rows.firstOrNull()?.cells?.size ?: 0
    require(rows.all { it.cells.size == columns }) { "Heatmap rows must all have $columns cells" }
    // The tap handler lives across recompositions: read the latest rows and callback, not the first ones.
    val currentRows by rememberUpdatedState(rows)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP), modifier = Modifier.width(ROW_LABEL_WIDTH)) {
            Spacer(modifier = Modifier.height(COLUMN_LABEL_HEIGHT))
            rows.forEach { row ->
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.height(CELL_HEIGHT)) {
                    Text(text = row.label, style = labelStyle.copy(fontWeight = FontWeight.Medium), color = colors.textSecondary)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP), modifier = Modifier.height(COLUMN_LABEL_HEIGHT)) {
                columnLabels.forEach { label ->
                    Text(
                        text = label,
                        style = OneMindTheme.typography.chartLabel,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val grid = currentRows
                            val gridColumns = grid.firstOrNull()?.cells?.size ?: 0
                            nearestCell(offset.x, offset.y, size.width.toFloat(), size.height.toFloat(), grid.size, gridColumns)
                                ?.let { (row, column) -> currentOnSelect(row, column) }
                        }
                    },
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        row.cells.forEachIndexed { columnIndex, cell ->
                            val isSelected = selected == rowIndex to columnIndex
                            val shape = RoundedCornerShape(OneMindTheme.radius.heatCell)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(CELL_HEIGHT)
                                        .background(colors.heatColor(cell.level), shape)
                                        .border(2.dp, if (isSelected) colors.selection else Color.Transparent, shape)
                                        // Accessibility only; touches go through the grid's tap handler above.
                                        .semantics {
                                            contentDescription = cell.label
                                            role = Role.Button
                                            this.selected = isSelected
                                            onClick {
                                                onSelect(rowIndex, columnIndex)
                                                true
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The cell for a touch at ([x], [y]) in a cell area of [width] × [height] px with [rows] × [columns]
 * equal-weight cells and fixed gaps between them. The area is split into equal slots (`width / columns`
 * by `height / rows`). With equal weights and gaps, every pixel of cell k lies inside slot k, so a touch
 * on a cell always selects that cell; a touch in a gap goes to one of its two neighbours (the split is
 * within half a gap of the gap's middle). Touches outside the area clamp to the edge cells. `null` for an
 * empty grid.
 */
internal fun nearestCell(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    rows: Int,
    columns: Int,
): Pair<Int, Int>? {
    if (rows <= 0 || columns <= 0 || width <= 0f || height <= 0f) return null
    val row = (y / height * rows).toInt().coerceIn(0, rows - 1)
    val column = (x / width * columns).toInt().coerceIn(0, columns - 1)
    return row to column
}

private val CELL_HEIGHT = 28.dp
private val CELL_GAP = 4.dp
private val ROW_GAP = 5.dp
private val ROW_LABEL_WIDTH = 30.dp
private val COLUMN_LABEL_HEIGHT = 16.dp

/** Heat ramp colour for [level], clamped to the ramp. */
fun OneMindColors.heatColor(level: Int): Color = heatRamp[level.coerceIn(0, heatRamp.lastIndex)]
