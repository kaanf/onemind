package com.onemind.backend.reporting.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** One stored discount type of a venue's day (`reporting.discount_daily`): item discounts from the POS. */
data class TypedDiscountDay(
    val day: LocalDate,
    /** The POS `discountType`; `UNSPECIFIED` for an item discount without one. */
    val type: String,
    val records: Int,
    val amountMinor: Long,
)

/** One venue's input to Discounts & losses: its stored hours and typed item discounts of the span. */
data class VenueLosses(
    val venueId: UUID,
    val hours: List<HourTotals>,
    val typed: List<TypedDiscountDay>,
)

data class DiscountTypeTotals(
    val type: String,
    /** Null for [LossCalculator.UNTYPED]: bill-level amounts have no records. */
    val records: Int?,
    val amountMinor: Long,
)

data class LossRecords(
    /** Refunds only: null while a day of the span is stored by an older calculation (rule 2). Deleted bills always count. */
    val records: Int?,
    val amountMinor: Long,
)

/** `possibleLoss / paidRevenue`, kept exact; rounded once where it is shown. */
data class LossShare(
    val lossMinor: Long,
    val revenueMinor: Long,
) {
    init {
        require(revenueMinor != 0L) { "no share without revenue" }
    }

    /** The share in percent, one decimal half-up ("6.7"), from the exact ratio. */
    fun percent(): BigDecimal =
        BigDecimal.valueOf(lossMinor).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(revenueMinor), 1, RoundingMode.HALF_UP)
}

/** A venue's day whose typed item discounts are above its bill-level discounts (a data gap). */
data class DiscountMismatch(
    val venueId: UUID,
    val day: LocalDate,
)

data class LossFigures(
    val loss: LossTotals,
    val revenueMinor: Long,
    /** Null without revenue. */
    val share: LossShare?,
    val discountsByType: List<DiscountTypeTotals>,
    val refunds: LossRecords,
    val deletedBills: LossRecords,
    /** Discount records + refunded bills + deleted bills; null while a day is stored by an older calculation. */
    val records: Int?,
    val discountMismatches: List<DiscountMismatch>,
)

/**
 * Screen 02c from the stored hours and typed discounts of the venues with data (`docs/domain/METRICS.md`,
 * discounts and losses). Pure.
 *
 * - Possible loss = discounts + refunds + whole deleted bills (first release), discounts at bill level
 *   (the POS `discount`, as on Today).
 * - Discounts by type = item discounts by POS `discountType`, plus [UNTYPED] = bill-level − typed per venue
 *   and day when the bill level is higher (bill-wide discounts and the items of bills stored without
 *   lines), so the list adds up to the discounts component. A day whose typed discounts are higher shows
 *   its typed ones only and is a [DiscountMismatch] (data gap), never clamped.
 * - Loss share = possible loss / paidRevenue; its change is in percentage points.
 */
object LossCalculator {
    const val UNTYPED = "UNTYPED"

    /** [upToDate]: false while a day of the span predates [RollupVersion.CURRENT] (record counts would be 0). */
    fun compute(
        venues: List<VenueLosses>,
        upToDate: Boolean,
    ): LossFigures {
        val hours = venues.flatMap { it.hours }
        val loss = loss(hours)
        val revenue = hours.sumOf { it.paidMinor }
        val mismatches = mutableListOf<DiscountMismatch>()
        var untyped = 0L
        for (venue in venues) {
            val billLevel = venue.hours.groupBy { it.day }.mapValues { (_, inDay) -> inDay.sumOf { it.discountMinor } }
            val typed = venue.typed.groupBy { it.day }.mapValues { (_, inDay) -> inDay.sumOf { it.amountMinor } }
            for (day in (billLevel.keys + typed.keys).sorted()) {
                val difference = (billLevel[day] ?: 0) - (typed[day] ?: 0)
                if (difference > 0) untyped += difference
                if (difference < 0) mismatches += DiscountMismatch(venue.venueId, day)
            }
        }
        val byType =
            venues
                .flatMap { it.typed }
                .groupBy { it.type }
                .map { (type, days) -> DiscountTypeTotals(type, days.sumOf { it.records }, days.sumOf { it.amountMinor }) }
                .sortedWith(compareByDescending<DiscountTypeTotals> { it.amountMinor }.thenBy { it.type }) +
                listOfNotNull(if (untyped > 0) DiscountTypeTotals(UNTYPED, null, untyped) else null)
        val refundedBills = hours.sumOf { it.refundedBills }
        val deletedBills = hours.sumOf { it.deletedBills }
        val discountRecords = venues.flatMap { it.typed }.sumOf { it.records }
        return LossFigures(
            loss = loss,
            revenueMinor = revenue,
            share = share(loss.totalMinor, revenue),
            discountsByType = byType,
            refunds = LossRecords(if (upToDate) refundedBills else null, loss.refundsMinor),
            deletedBills = LossRecords(deletedBills, loss.deletedBillsMinor),
            records = if (upToDate) discountRecords + refundedBills + deletedBills else null,
            discountMismatches = mismatches,
        )
    }

    fun loss(hours: List<HourTotals>) =
        LossTotals(
            discountsMinor = hours.sumOf { it.discountMinor },
            refundsMinor = hours.sumOf { it.refundedMinor },
            deletedBillsMinor = hours.sumOf { it.deletedMinor },
        )

    /** Null without revenue. */
    fun share(
        lossMinor: Long,
        revenueMinor: Long,
    ): LossShare? = if (revenueMinor == 0L) null else LossShare(lossMinor, revenueMinor)

    /**
     * The change of the loss share in percentage points, rounded once (one decimal half-up) from the exact
     * shares: `(l1·r2 − l2·r1) × 100 / (r1·r2)`. Null when either share is missing.
     */
    fun changePoints(
        current: LossShare?,
        previous: LossShare?,
    ): BigDecimal? {
        if (current == null || previous == null) return null
        val numerator =
            BigDecimal
                .valueOf(current.lossMinor)
                .multiply(BigDecimal.valueOf(previous.revenueMinor))
                .subtract(BigDecimal.valueOf(previous.lossMinor).multiply(BigDecimal.valueOf(current.revenueMinor)))
                .multiply(BigDecimal(100))
        val denominator = BigDecimal.valueOf(current.revenueMinor).multiply(BigDecimal.valueOf(previous.revenueMinor))
        return numerator.divide(denominator, 1, RoundingMode.HALF_UP)
    }
}
