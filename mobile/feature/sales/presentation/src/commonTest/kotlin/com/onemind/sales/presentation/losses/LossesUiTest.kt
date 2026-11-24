package com.onemind.sales.presentation.losses

import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.sales.domain.losses.LossesComparison
import com.onemind.sales.domain.losses.LossesDataGaps
import com.onemind.sales.domain.losses.UnavailableLossComponent
import com.onemind.sales.presentation.losses.LossesFixtures.ready
import com.onemind.sales.presentation.shown
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LossesUiTest {
    @Test
    fun formatsTheHeroAsSent() {
        val ui = ready.toUi()

        assertEquals(shown("22·410_Kč"), ui.possibleLoss)
        assertEquals(LossChange("+0.8", LossChange.Direction.UP), ui.change)
        assertEquals(240, ui.records)
        assertEquals("6.7", ui.sharePercent)
    }

    @Test
    fun rowsAreLossesInTheDesignOrderWithoutAnInventedDiscountCount() {
        val rows = ready.toUi().rows

        assertEquals(listOf(LossRowKind.DISCOUNTS, LossRowKind.DELETED_BILLS, LossRowKind.REFUNDS), rows.map { it.kind })
        assertEquals(shown("−18·640_Kč"), rows[0].amount)
        assertNull(rows[0].records)
        assertEquals(2, rows[1].records)
        assertEquals(shown("−890_Kč"), rows[2].amount)
    }

    @Test
    fun missingComponentsAreLeftOut() {
        val rows = ready.copy(discounts = null, refunds = null).toUi().rows

        assertEquals(listOf(LossRowKind.DELETED_BILLS), rows.map { it.kind })
    }

    @Test
    fun discountTypesAreNamed() {
        val names = ready.toUi().discountTypes.map { it.name }

        assertEquals(listOf(DiscountName.Pos("Happy hour"), DiscountName.Unspecified, DiscountName.Other), names)
    }

    @Test
    fun aLowerShareIsADropAndNoChangeIsFlat() {
        fun change(points: String?) =
            ready
                .copy(comparison = LossesComparison(LocalDate(2026, 10, 26), LocalDate(2026, 11, 1), points?.let(DecimalValue::parse)))
                .toUi()
                .change

        assertEquals(LossChange("−1.2", LossChange.Direction.DOWN), change("-1.2"))
        assertEquals(LossChange("0.0", LossChange.Direction.FLAT), change("0.0"))
        assertNull(change(null))
        assertNull(ready.copy(comparison = null).toUi().change)
    }

    @Test
    fun unavailableAndGapsAreFlagged() {
        val ui = ready.toUi()
        assertEquals(listOf(UnavailableLossComponent.ITEM_VOIDS, UnavailableLossComponent.STOCK_CORRECTIONS), ui.unavailable)
        assertFalse(ui.hasDataGaps)
        assertTrue(ready.copy(dataGaps = LossesDataGaps(1, 0, 0, 0, 0)).toUi().hasDataGaps)
    }
}
