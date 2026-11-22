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
    /** Null while a day of the span is stored by an older calculation (rule 2). */
    val records: Int?,
    val amountMinor: Long,
)

/** A venue's day whose typed item discounts are above its bill-level discounts (a data gap). */
data class DiscountMismatch(
    val venueId: UUID,
    val day: LocalDate,
)

data class LossFigures(
    val loss: LossTotals,
    val revenueMinor: Long,
    /** `possibleLoss / paidRevenue × 100`, exact to 6 decimals; null without revenue. */
    val sharePercent: BigDecimal?,
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
            sharePercent = sharePercent(loss.totalMinor, revenue),
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

    /** `loss / revenue × 100`, 6 decimals half-up; null without revenue. */
    fun sharePercent(
        lossMinor: Long,
        revenueMinor: Long,
    ): BigDecimal? =
        if (revenueMinor == 0L) {
            null
        } else {
            BigDecimal.valueOf(lossMinor).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(revenueMinor), 6, RoundingMode.HALF_UP)
        }

    /** The change of the loss share in percentage points, one decimal half-up; null when either share is missing. */
    fun changePoints(
        current: BigDecimal?,
        previous: BigDecimal?,
    ): BigDecimal? = if (current == null || previous == null) null else current.subtract(previous).setScale(1, RoundingMode.HALF_UP)
}
