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
                    LossRow(LossRowKind.DISCOUNTS, null, "−18 640 Kč"),
                    LossRow(LossRowKind.DELETED_BILLS, 2, "−760 Kč"),
                    LossRow(LossRowKind.REFUNDS, 4, "−890 Kč"),
                ),
            discountTypes =
                listOf(
                    DiscountRow(DiscountName.Pos("Happy hour"), 120, "−9 870 Kč"),
                    DiscountRow(DiscountName.Pos("Staff"), 60, "−5 200 Kč"),
                    DiscountRow(DiscountName.Other, null, "−3 570 Kč"),
                ),
            unavailable = listOf(UnavailableLossComponent.ITEM_VOIDS, UnavailableLossComponent.UNPAID_CLOSED),
            hasDataGaps = false,
        )
}
