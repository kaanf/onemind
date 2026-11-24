package com.onemind.sales.presentation.losses

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import com.onemind.sales.domain.losses.DiscountTypeLoss
import com.onemind.sales.domain.losses.LossRecords
import com.onemind.sales.domain.losses.LossesComparison
import com.onemind.sales.domain.losses.LossesDataGaps
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesSummary
import com.onemind.sales.domain.losses.UnavailableLossComponent
import kotlinx.datetime.LocalDate

internal object LossesFixtures {
    fun czk(major: Long) = Money(major * 100, Currency.CZK)

    val ready =
        LossesSummary(
            hasData = true,
            currency = Currency.CZK,
            period = LossesPeriod.WEEK,
            from = LocalDate(2026, 11, 2),
            to = LocalDate(2026, 11, 8),
            finished = false,
            pendingVenueCount = 0,
            possibleLoss = czk(22_410),
            sharePercent = DecimalValue.parse("6.7"),
            records = 240,
            comparison = LossesComparison(LocalDate(2026, 10, 26), LocalDate(2026, 11, 1), DecimalValue.parse("0.8")),
            discountsByType =
                listOf(
                    DiscountTypeLoss("HAPPY_HOUR", 120, czk(9_870)),
                    DiscountTypeLoss(DiscountTypeLoss.UNSPECIFIED, 3, czk(100)),
                    DiscountTypeLoss(DiscountTypeLoss.UNTYPED, null, czk(8_670)),
                ),
            discounts = czk(18_640),
            refunds = LossRecords(4, czk(890)),
            deletedBills = LossRecords(2, czk(2_880)),
            unavailable = setOf(UnavailableLossComponent.STOCK_CORRECTIONS, UnavailableLossComponent.ITEM_VOIDS),
            dataGaps = LossesDataGaps(0, 0, 0, 0, 0),
        )
}
