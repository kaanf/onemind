package com.onemind.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.core.designsystem.theme.OneMindColors
import com.onemind.core.designsystem.theme.OneMindTheme

/** How a table tile is filled. */
sealed interface TileTone {
    /** *Now*: no open tab. */
    data object Free : TileTone

    /** *Now*: open tab. */
    data object Busy : TileTone

    /** *This week*: step of the tables ramp (0 = lowest). */
    data class Ramp(
        val step: Int,
    ) : TileTone
}

/** A table tile; [spanRow] for the bar counter; [label] is spoken (*T1, open tab 1 240 Kč*). */
data class FloorTile(
    val name: String,
    val value: String,
    val tone: TileTone,
    val label: String,
    val spanRow: Boolean = false,
)

/** Grid of table tiles ([columns] per row, min 58 high, radius 10); the selected tile has a `selection` outline. */
@Composable
fun FloorPlan(
    tiles: List<FloorTile>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
        floorPlanRows(tiles, columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.tileGap)) {
                row.forEach { index ->
                    Tile(
                        tile = tiles[index],
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(if (tiles[index].spanRow) columns.toFloat() else 1f),
                    )
                }
                val missing = columns - row.sumOf { if (tiles[it].spanRow) columns else 1 }
                if (missing > 0) Spacer(modifier = Modifier.weight(missing.toFloat()))
            }
        }
    }
}

@Composable
private fun Tile(
    tile: FloorTile,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val (background, content) = OneMindTheme.colors.tileColors(tile.tone)
    val shape = RoundedCornerShape(OneMindTheme.radius.chip)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier =
            modifier
                .heightIn(min = 58.dp)
                .background(background, shape)
                .border(2.dp, if (selected) OneMindTheme.colors.selection else Color.Transparent, shape)
                .selectable(selected = selected, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = tile.label
                    role = Role.Button
                    this.selected = selected
                }.padding(6.dp),
    ) {
        Text(text = tile.name, style = OneMindTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = content)
        Text(text = tile.value, style = OneMindTheme.typography.caption, color = content.copy(alpha = 0.8f), maxLines = 1)
    }
}

/** Splits tiles into rows of [columns]; a [FloorTile.spanRow] tile takes a whole row. Returns tile indices. */
internal fun floorPlanRows(
    tiles: List<FloorTile>,
    columns: Int,
): List<List<Int>> {
    val rows = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    tiles.forEachIndexed { index, tile ->
        if (tile.spanRow) {
            if (current.isNotEmpty()) rows += current
            rows += listOf(index)
            current = mutableListOf()
        } else {
            current += index
            if (current.size == columns) {
                rows += current
                current = mutableListOf()
            }
        }
    }
    if (current.isNotEmpty()) rows += current
    return rows
}

/** Background and content colour for a tile; text turns white from ramp step 5 (index 4). */
internal fun OneMindColors.tileColors(tone: TileTone): Pair<Color, Color> =
    when (tone) {
        TileTone.Free -> {
            surfaceMuted to textSecondary
        }

        TileTone.Busy -> {
            textPrimary to onAccent
        }

        is TileTone.Ramp -> {
            val step = tone.step.coerceIn(0, tablesRamp.lastIndex)
            tablesRamp[step] to if (step >= 4) onAccent else textPrimary
        }
    }
