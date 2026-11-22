package com.onemind.backend.reporting.domain

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.common.time.BusinessDayRange
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The version of the rollup calculation below. Bump it when a METRICS definition changes: the rebuild
 * job then recomputes every stored day (`rollup_state.rollup_version`).
 */
object RollupVersion {
    /** 2: `paidBills` and the refunded card/cash/other split (V013). 3: `refundedBills` (V014). */
    const val CURRENT = 3
}

// Inputs: the POS facts of one venue and business day (pos/api/PosReadModel), amounts in minor units.

data class RollupPayment(
    /** `CARD`, `CASH` or `OTHER`. */
    val kind: String,
    val amountMinor: Long,
)

data class RollupBill(
    val billId: UUID,
    val paidAt: Instant,
    val sessionStartedAt: Instant?,
    val totalMinor: Long,
    val totalWithoutTaxMinor: Long?,
    val discountMinor: Long,
    val tipsMinor: Long,
    val refunded: Boolean,
    val deleted: Boolean,
    val deskId: UUID?,
    val createdByStaffId: UUID?,
    val paidByStaffId: UUID?,
    val guestCount: Int?,
    val payments: List<RollupPayment>,
)

data class RollupDiscount(
    val type: String?,
    val amountMinor: Long?,
)

data class RollupLine(
    val billId: UUID,
    val productId: UUID?,
    val quantity: BigDecimal,
    val discountMinor: Long,
    val totalMinor: Long,
    val discounts: List<RollupDiscount>,
)

// Outputs: one row per table key (V004), hours are local clock hours (0–23).

data class HourlySalesRow(
    val hour: Int,
    val revenueMinor: Long,
    val revenueWithoutTaxMinor: Long,
    /** Bills without a net total from the POS: [revenueWithoutTaxMinor] leaves them out (rule 2). */
    val billsMissingNet: Int,
    /** Bills whose payments don't add up (tips above the payments, or a total without payments): left out of revenue. */
    val billsPaymentMismatch: Int,
    val bills: Int,
    /** Bills that count in paidRevenue: not deleted, not refunded, no payment mismatch (averageBill). */
    val paidBills: Int,
    val guests: Int,
    val tipsMinor: Long,
    val discountMinor: Long,
    val cardMinor: Long,
    val cashMinor: Long,
    val otherMinor: Long,
    val refundedMinor: Long,
    /** Refunded bills (the records behind [refundedMinor]). */
    val refundedBills: Int,
    /** [refundedMinor] by payment kind, so card + cash + other of paidRevenue = card − refundedCard, … */
    val refundedCardMinor: Long,
    val refundedCashMinor: Long,
    val refundedOtherMinor: Long,
    val deletedBills: Int,
    val deletedMinor: Long,
)

/** The row as a reader sees a stored hour of [day] (`RollupRepository.hours` reads the same columns). */
fun HourlySalesRow.toTotals(day: LocalDate) =
    HourTotals(
        day = day,
        hour = hour,
        revenueMinor = revenueMinor,
        refundedMinor = refundedMinor,
        tipsMinor = tipsMinor,
        guests = guests,
        discountMinor = discountMinor,
        deletedMinor = deletedMinor,
        paymentMismatchBills = billsPaymentMismatch,
        paidBills = paidBills,
        refundedBills = refundedBills,
        deletedBills = deletedBills,
        paidCardMinor = cardMinor - refundedCardMinor,
        paidCashMinor = cashMinor - refundedCashMinor,
        paidOtherMinor = otherMinor - refundedOtherMinor,
    )

data class HourlyActivityRow(
    val hour: Int,
    val sessions: Int,
    val guests: Int,
)

data class ProductHourlyRow(
    val hour: Int,
    val productId: UUID,
    val quantity: BigDecimal,
    val grossMinor: Long,
    val discountMinor: Long,
)

data class StaffDailyRow(
    val staffId: UUID,
    val bills: Int,
    val revenueMinor: Long,
    val guests: Int,
    val discountMinor: Long,
    val deletedBills: Int,
    val tipsMinor: Long,
)

data class DeskDailyRow(
    val deskId: UUID,
    val bills: Int,
    val revenueMinor: Long,
    val guests: Int,
    val occupiedMinutes: Int,
)

data class DiscountDailyRow(
    val discountType: String,
    val records: Int,
    val amountMinor: Long,
)

data class DayRollups(
    val hourlySales: List<HourlySalesRow>,
    val hourlyActivity: List<HourlyActivityRow>,
    val productHourly: List<ProductHourlyRow>,
    val staffDaily: List<StaffDailyRow>,
    val deskDaily: List<DeskDailyRow>,
    val discountDaily: List<DiscountDailyRow>,
    /** The bills behind `billsPaymentMismatch`, for the refresh to name in its log. */
    val paymentMismatchBillIds: List<UUID> = emptyList(),
)

/**
 * `paidRevenue` of single bills (`docs/domain/METRICS.md`, revenue), the one definition rollups and the
 * Today comparison share: a bill's payments without its tips. Tips come off card payments first, then
 * cash, then other (M14). A bill whose payments don't add up (tips above the payments, or a total
 * without any payment) counts nothing: it is a data gap (rule 2), never clamped.
 */
object BillRevenue {
    const val CARD = "CARD"
    const val CASH = "CASH"
    const val OTHER = "OTHER"
    private val KINDS = listOf(CARD, CASH, OTHER)

    fun isPaymentMismatch(bill: RollupBill): Boolean =
        !bill.deleted && (bill.tipsMinor > bill.payments.sumOf { it.amountMinor } || (bill.totalMinor > 0 && bill.payments.isEmpty()))

    /** Payments per kind without the bill's tips; all 0 for a payment mismatch. */
    fun byKind(bill: RollupBill): Map<String, Long> {
        if (isPaymentMismatch(bill)) return KINDS.associateWith { 0L }
        val amounts = KINDS.associateWith { kind -> bill.payments.filter { it.kind == kind }.sumOf { it.amountMinor } }.toMutableMap()
        var tips = bill.tipsMinor
        for (kind in KINDS) {
            val taken = minOf(tips, amounts.getValue(kind).coerceAtLeast(0))
            amounts[kind] = amounts.getValue(kind) - taken
            tips -= taken
        }
        return amounts
    }

    /** The bill's revenue: its payments without its tips (0 for a payment mismatch). */
    fun revenue(bill: RollupBill): Long = byKind(bill).values.sum()

    /**
     * `paidRevenue` of [bills]: revenue of bills that are paid, not deleted and not refunded (a refund
     * counts in the bill's paid business day, M13). The same sum as rollups' revenue − refunded.
     */
    fun paid(bills: Collection<RollupBill>): Long = bills.filter { !it.deleted && !it.refunded }.sumOf { revenue(it) }
}

/**
 * The rollups of one venue and business day from its bills and lines (`docs/domain/METRICS.md`). Pure:
 * the same facts always give the same rows, so a refresh can replace a day whenever its facts change.
 *
 * - Revenue (`paidRevenue`) = the bill's payments without its tips, for paid bills that aren't deleted,
 *   in the hour of `paidAt`; so card + cash + other = revenue. Refunded bills are included: they also
 *   count as refunded in their paid business day (M13: Storyous has no refund time), and readers
 *   subtract `refundedMinor`. Tips come off card when the bill has a card payment, else off cash, then
 *   other (M14; an approximation for mixed payments).
 * - A bill whose payments don't add up (tips above the payments, or a total without any payment) is left
 *   out of revenue and the card/cash/other split and counted as `billsPaymentMismatch` (rule 2).
 * - The net (without tax) revenue sums only bills that have a net total; the others are counted, so a
 *   reader knows the figure is incomplete (rule 2, M2 open). A discount without an amount fails the day:
 *   such bills are quarantined by the sync, so one here is a bug, never a 0.
 * - Guests: the POS count, else 1 per bill (M5).
 * - Products and opened-bill activity use the hour the bill was opened (session start, else paid time):
 *   Storyous has no per-item time (MVP_SCOPE). Deleted bills count only as deleted.
 * - Staff: sales, guests, discounts and deleted bills by who opened the bill, tips by who closed it (M6).
 */
object RollupCalculator {
    fun compute(
        bills: List<RollupBill>,
        lines: List<RollupLine>,
        day: BusinessDayRange,
        zone: ZoneId,
    ): DayRollups {
        val kept = bills.filterNot { it.deleted }
        val keptIds = kept.map { it.billId }.toSet()
        val openedHour = bills.associate { it.billId to BusinessDayCalendar.localHour(it.sessionStartedAt ?: it.paidAt, zone) }
        return DayRollups(
            hourlySales = hourlySales(bills, zone),
            hourlyActivity =
                kept
                    .groupBy { openedHour.getValue(it.billId) }
                    .map { (hour, inHour) -> HourlyActivityRow(hour, inHour.size, inHour.sumOf { it.guests() }) }
                    .sortedBy { it.hour },
            productHourly =
                lines
                    .filter { it.billId in keptIds && it.productId != null }
                    .groupBy { openedHour.getValue(it.billId) to requireNotNull(it.productId) }
                    .map { (key, inKey) ->
                        ProductHourlyRow(
                            hour = key.first,
                            productId = key.second,
                            quantity = inKey.fold(BigDecimal.ZERO) { sum, line -> sum + line.quantity },
                            grossMinor = inKey.sumOf { it.totalMinor },
                            discountMinor = inKey.sumOf { it.discountMinor },
                        )
                    }.sortedWith(compareBy({ it.hour }, { it.productId })),
            staffDaily = staffDaily(bills),
            deskDaily =
                kept
                    .filter { it.deskId != null }
                    .groupBy { requireNotNull(it.deskId) }
                    .map { (deskId, onDesk) ->
                        DeskDailyRow(
                            deskId = deskId,
                            bills = onDesk.size,
                            revenueMinor = onDesk.sumOf { BillRevenue.revenue(it) },
                            guests = onDesk.sumOf { it.guests() },
                            occupiedMinutes = onDesk.sumOf { occupiedMinutes(it, day) },
                        )
                    }.sortedBy { it.deskId },
            discountDaily =
                lines
                    .filter { it.billId in keptIds }
                    .flatMap { it.discounts }
                    .groupBy { it.type ?: UNSPECIFIED }
                    .map { (type, records) -> DiscountDailyRow(type, records.size, records.sumOf { it.requiredAmount() }) }
                    .sortedBy { it.discountType },
            paymentMismatchBillIds = bills.filter { BillRevenue.isPaymentMismatch(it) }.map { it.billId },
        )
    }

    private fun hourlySales(
        bills: List<RollupBill>,
        zone: ZoneId,
    ): List<HourlySalesRow> =
        bills
            .groupBy { BusinessDayCalendar.localHour(it.paidAt, zone) }
            .map { (hour, inHour) ->
                val kept = inHour.filterNot { it.deleted }
                val byKind = kept.map { BillRevenue.byKind(it) }
                val refundedByKind = kept.filter { it.refunded }.map { BillRevenue.byKind(it) }
                HourlySalesRow(
                    hour = hour,
                    revenueMinor = byKind.sumOf { it.values.sum() },
                    revenueWithoutTaxMinor = kept.mapNotNull { it.totalWithoutTaxMinor }.sum(),
                    billsMissingNet = kept.count { it.totalWithoutTaxMinor == null },
                    billsPaymentMismatch = kept.count { BillRevenue.isPaymentMismatch(it) },
                    bills = kept.size,
                    paidBills = kept.count { !it.refunded && !BillRevenue.isPaymentMismatch(it) },
                    guests = kept.sumOf { it.guests() },
                    tipsMinor = kept.sumOf { it.tipsMinor },
                    discountMinor = kept.sumOf { it.discountMinor },
                    cardMinor = byKind.sumOf { it.getValue(BillRevenue.CARD) },
                    cashMinor = byKind.sumOf { it.getValue(BillRevenue.CASH) },
                    otherMinor = byKind.sumOf { it.getValue(BillRevenue.OTHER) },
                    refundedMinor = kept.filter { it.refunded }.sumOf { BillRevenue.revenue(it) },
                    refundedBills = kept.count { it.refunded },
                    refundedCardMinor = refundedByKind.sumOf { it.getValue(BillRevenue.CARD) },
                    refundedCashMinor = refundedByKind.sumOf { it.getValue(BillRevenue.CASH) },
                    refundedOtherMinor = refundedByKind.sumOf { it.getValue(BillRevenue.OTHER) },
                    deletedBills = inHour.count { it.deleted },
                    deletedMinor = inHour.filter { it.deleted }.sumOf { it.totalMinor },
                )
            }.sortedBy { it.hour }

    private fun staffDaily(bills: List<RollupBill>): List<StaffDailyRow> {
        val kept = bills.filterNot { it.deleted }
        val opened = bills.filter { it.createdByStaffId != null }.groupBy { requireNotNull(it.createdByStaffId) }
        val tips = kept.filter { it.paidByStaffId != null }.groupBy { requireNotNull(it.paidByStaffId) }
        return (opened.keys + tips.keys)
            .map { staffId ->
                val own = opened[staffId].orEmpty()
                val ownKept = own.filterNot { it.deleted }
                StaffDailyRow(
                    staffId = staffId,
                    bills = ownKept.size,
                    revenueMinor = ownKept.sumOf { BillRevenue.revenue(it) },
                    guests = ownKept.sumOf { it.guests() },
                    discountMinor = ownKept.sumOf { it.discountMinor },
                    deletedBills = own.count { it.deleted },
                    tipsMinor = tips[staffId].orEmpty().sumOf { it.tipsMinor },
                )
            }.sortedBy { it.staffId }
    }

    /** Minutes the bill's table was taken within the business day: from opened to paid, clipped to the day. */
    private fun occupiedMinutes(
        bill: RollupBill,
        day: BusinessDayRange,
    ): Int {
        val opened = maxOf(bill.sessionStartedAt ?: bill.paidAt, day.start)
        val paid = minOf(bill.paidAt, day.end)
        return if (paid.isAfter(opened)) Duration.between(opened, paid).toMinutes().toInt() else 0
    }

    private fun RollupDiscount.requiredAmount(): Long =
        amountMinor ?: error("A stored discount without an amount (type ${type ?: UNSPECIFIED}): the sync quarantines those")

    // M5: a bill without a guest count counts as one guest.
    private fun RollupBill.guests(): Int = guestCount ?: 1

    private const val UNSPECIFIED = "UNSPECIFIED"
}
