package com.onemind.sales.domain.losses

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import com.onemind.core.domain.quantity.DecimalValue
import kotlinx.datetime.LocalDate

/** The periods 02c offers; losses cover whole business days, so there is no hour. */
enum class LossesPeriod { DAY, WEEK, MONTH }

/**
 * Discounts & losses of one period (`docs/domain/METRICS.md`, discounts and losses). Figures are `null`
 * when no requested venue has data yet ([hasData] false) or the server can't give them: never 0 (rule 2).
 */
data class LossesSummary(
    /** `false` while no requested venue has had its first sync (`NO_DATA_YET`). */
    val hasData: Boolean,
    val currency: Currency,
    val period: LossesPeriod,
    /** First and last business day of the period. */
    val from: LocalDate,
    val to: LocalDate,
    /** The period is over (not still running). */
    val finished: Boolean,
    /** Venues still waiting for their first sync, left out of the figures. */
    val pendingVenueCount: Int,
    val possibleLoss: Money?,
    /** Possible loss as a share of paid revenue, one decimal ("6.7"). */
    val sharePercent: DecimalValue?,
    val records: Int?,
    val comparison: LossesComparison?,
    /** Largest first, *Other discounts* ([DiscountTypeLoss.UNTYPED]) last. */
    val discountsByType: List<DiscountTypeLoss>,
    /** The discounts part of [possibleLoss]; `null` when the server doesn't send it. */
    val discounts: Money?,
    val refunds: LossRecords?,
    val deletedBills: LossRecords?,
    /** Parts of the possible loss the POS can't give in the first release. */
    val unavailable: Set<UnavailableLossComponent>,
    val dataGaps: LossesDataGaps,
)

/** Against the same span of the previous period. */
data class LossesComparison(
    val previousFrom: LocalDate,
    val previousTo: LocalDate,
    /** Change of the loss share in percentage points ("0.8", "-1.2"); `null` without revenue on one side. */
    val changePoints: DecimalValue?,
)

/** Discounts of one POS discount type. [type] is the POS value, or [UNTYPED] / [UNSPECIFIED]. */
data class DiscountTypeLoss(
    val type: String,
    val records: Int?,
    val amount: Money,
) {
    companion object {
        /** Bill-level discounts above the typed ones (*Other discounts*); never has a record count. */
        const val UNTYPED = "UNTYPED"

        /** Item discounts without a type in the POS. */
        const val UNSPECIFIED = "UNSPECIFIED"
    }
}

data class LossRecords(
    val records: Int?,
    val amount: Money,
)

enum class UnavailableLossComponent { UNPAID_CLOSED, ITEM_VOIDS, STOCK_CORRECTIONS }

/** Bills or days missing from these figures (`DataGaps` in the API). */
data class LossesDataGaps(
    val quarantinedBillCount: Int,
    val linelessBillCount: Int,
    val outdatedBillCount: Int,
    val paymentMismatchBillCount: Int,
    val discountMismatchDayCount: Int,
) {
    val isEmpty: Boolean
        get() =
            quarantinedBillCount == 0 && linelessBillCount == 0 && outdatedBillCount == 0 &&
                paymentMismatchBillCount == 0 && discountMismatchDayCount == 0
}
