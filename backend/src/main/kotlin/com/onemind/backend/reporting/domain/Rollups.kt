package com.onemind.backend.reporting.domain

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.common.time.BusinessDayRange
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The version of the rollup calculation below. Bump it when a METRICS definition changes: the rebuild
 * job then recomputes every stored day (`rollup_state.rollup_version`).
 */
object RollupVersion {
    const val CURRENT = 1
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
    val bills: Int,
    val guests: Int,
    val tipsMinor: Long,
    val discountMinor: Long,
    val cardMinor: Long,
    val cashMinor: Long,
    val otherMinor: Long,
    val refundedMinor: Long,
    val deletedBills: Int,
    val deletedMinor: Long,
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
)

/**
 * The rollups of one venue and business day from its bills and lines (`docs/domain/METRICS.md`). Pure:
 * the same facts always give the same rows, so a refresh can replace a day whenever its facts change.
 *
 * - Revenue counts paid bills that aren't deleted, in the hour of `paidAt`. A refunded bill stays in its
 *   paid business day and also counts as refunded there (M13: Storyous has no refund time).
 * - Card / cash / other are the payments per kind without tips: tips come off card when the bill has a
 *   card payment, else off cash, then other (M14; an approximation for mixed payments).
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
                            revenueMinor = onDesk.sumOf { it.totalMinor },
                            guests = onDesk.sumOf { it.guests() },
                            occupiedMinutes = onDesk.sumOf { occupiedMinutes(it, day) },
                        )
                    }.sortedBy { it.deskId },
            discountDaily =
                lines
                    .filter { it.billId in keptIds }
                    .flatMap { it.discounts }
                    .groupBy { it.type ?: UNSPECIFIED }
                    .map { (type, records) -> DiscountDailyRow(type, records.size, records.sumOf { it.amountMinor ?: 0 }) }
                    .sortedBy { it.discountType },
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
                val byKind = kept.map { paymentsWithoutTips(it) }
                HourlySalesRow(
                    hour = hour,
                    revenueMinor = kept.sumOf { it.totalMinor },
                    revenueWithoutTaxMinor = kept.sumOf { it.totalWithoutTaxMinor ?: 0 },
                    bills = kept.size,
                    guests = kept.sumOf { it.guests() },
                    tipsMinor = kept.sumOf { it.tipsMinor },
                    discountMinor = kept.sumOf { it.discountMinor },
                    cardMinor = byKind.sumOf { it.getValue(CARD) },
                    cashMinor = byKind.sumOf { it.getValue(CASH) },
                    otherMinor = byKind.sumOf { it.getValue(OTHER) },
                    refundedMinor = kept.filter { it.refunded }.sumOf { it.totalMinor },
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
                    revenueMinor = ownKept.sumOf { it.totalMinor },
                    guests = ownKept.sumOf { it.guests() },
                    discountMinor = ownKept.sumOf { it.discountMinor },
                    deletedBills = own.count { it.deleted },
                    tipsMinor = tips[staffId].orEmpty().sumOf { it.tipsMinor },
                )
            }.sortedBy { it.staffId }
    }

    /** Payments per kind with the bill's tips taken off: card first, then cash, then other (M14). */
    private fun paymentsWithoutTips(bill: RollupBill): Map<String, Long> {
        val amounts = KINDS.associateWith { kind -> bill.payments.filter { it.kind == kind }.sumOf { it.amountMinor } }.toMutableMap()
        var tips = bill.tipsMinor
        for (kind in KINDS) {
            val taken = minOf(tips, amounts.getValue(kind).coerceAtLeast(0))
            amounts[kind] = amounts.getValue(kind) - taken
            tips -= taken
        }
        return amounts
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

    // M5: a bill without a guest count counts as one guest.
    private fun RollupBill.guests(): Int = guestCount ?: 1

    private const val CARD = "CARD"
    private const val CASH = "CASH"
    private const val OTHER = "OTHER"
    private val KINDS = listOf(CARD, CASH, OTHER)
    private const val UNSPECIFIED = "UNSPECIFIED"
}
