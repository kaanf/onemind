package com.onemind.sales.presentation.losses

import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.UnavailableLossComponent
import kotlinx.datetime.LocalDate

/** Sample figures for previews only (never in production code paths, rule 2). */
internal object LossesPreviewData {
    val ready =
        LossesUi(
            period = LossesPeriod.WEEK,
            from = LocalDate(2026, 11, 2),
            to = LocalDate(2026, 11, 8),
            hasData = true,
            pendingVenueCount = 0,
            possibleLoss = "22 410 Kč",
            change = LossChange("+0.8", LossChange.Direction.UP),
            records = 240,
            sharePercent = "6.7",
            rows =
                listOf(
                    LossRow(LossRowKind.DISCOUNTS, null, "−18 640"),
                    LossRow(LossRowKind.DELETED_BILLS, 2, "−760"),
                    LossRow(LossRowKind.REFUNDS, 4, "−890"),
                ),
            discountTypes =
                listOf(
                    DiscountRow(DiscountName.Pos("Happy Hour"), 120, "−9 870"),
                    DiscountRow(DiscountName.Pos("Staff"), 60, "−5 200"),
                    DiscountRow(DiscountName.Other, null, "−3 570"),
                ),
            unavailable = listOf(UnavailableLossComponent.ITEM_VOIDS, UnavailableLossComponent.UNPAID_CLOSED),
            hasDataGaps = false,
        )
}
