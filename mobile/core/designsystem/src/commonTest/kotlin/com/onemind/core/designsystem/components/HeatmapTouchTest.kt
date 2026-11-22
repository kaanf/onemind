package com.onemind.core.designsystem.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The nearest-cell mapping behind the heatmap's touch target exception (DESIGN_SYSTEM.md). */
class HeatmapTouchTest {
    // 7 days × 12 hours on a 350 × 231 px cell area (more columns than the canvas, to cover longer openings).
    private val width = 350f
    private val height = 231f

    private fun cell(
        x: Float,
        y: Float,
    ) = nearestCell(x, y, width, height, rows = 7, columns = 12)

    @Test
    fun centresMapToTheirCell() {
        assertEquals(0 to 0, cell(14f, 16f))
        assertEquals(3 to 6, cell(6.5f * width / 12, 3.5f * height / 7))
        assertEquals(6 to 11, cell(width - 1f, height - 1f))
    }

    @Test
    fun gapsBetweenCellsBelongToTheNearestCell() {
        val slot = width / 12
        // Just left and just right of the boundary between columns 2 and 3.
        assertEquals(0 to 2, cell(3 * slot - 0.5f, 5f))
        assertEquals(0 to 3, cell(3 * slot + 0.5f, 5f))
    }

    @Test
    fun touchesOutsideTheAreaClampToTheEdge() {
        assertEquals(0 to 0, cell(-10f, -10f))
        assertEquals(6 to 11, cell(width + 40f, height + 40f))
    }

    @Test
    fun everyPointOfTheAreaHitsACell() {
        var x = 0f
        while (x < width) {
            var y = 0f
            while (y < height) {
                val hit = cell(x, y)!!
                assertTrue(hit.first in 0..6 && hit.second in 0..11, "$x,$y -> $hit")
                y += 3f
            }
            x += 3f
        }
    }

    @Test
    fun emptyGridHasNoCell() {
        assertNull(nearestCell(10f, 10f, width, height, rows = 0, columns = 12))
        assertNull(nearestCell(10f, 10f, 0f, height, rows = 7, columns = 12))
    }
}
