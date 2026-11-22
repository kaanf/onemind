package com.onemind.backend.pos.api

import com.onemind.backend.pos.application.ReadPosFacts
import com.onemind.backend.pos.domain.DiscountAmount
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A complete page; [next] continues after its last row, `null` on the last page. */
data class FactPage<T>(
    val items: List<T>,
    val next: UUID?,
)

data class PaymentFact(
    /** `CARD`, `CASH` or `OTHER`. */
    val kind: String,
    val amountMinor: Long,
)

/** A paid bill as other modules see it; amounts in minor units of the venue currency. */
data class BillFact(
    val billId: UUID,
    val businessDay: LocalDate,
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
    val modifiedAt: Instant,
    /** Stored from the header only: counts in bill figures, has no lines. */
    val linesMissing: Boolean,
    val payments: List<PaymentFact>,
)

data class DiscountFact(
    /** The POS's raw discount type, `null` if it sent none. */
    val type: String?,
    val amountMinor: Long?,
)

data class BillLineFact(
    val lineId: UUID,
    val billId: UUID,
    val productId: UUID?,
    val quantity: BigDecimal,
    val discountMinor: Long,
    /** Quantity × unit price − discount. */
    val totalMinor: Long,
    val discounts: List<DiscountFact>,
)

data class DataGaps(
    /** Bills that couldn't be read at all: missing from every figure. */
    val quarantinedBills: Int,
    /** Bills stored without their lines: in bill figures, missing from product and discount figures. */
    val billsWithoutLines: Int,
    /** Bills whose newer version couldn't be read: their stored figures may be out of date. */
    val outdatedBills: Int,
)

/**
 * The POS read API (ADR 0013 §5, amended 2026-11-22): `reporting` and `stock` read POS facts only here,
 * never the `pos` tables. Pages are complete when returned (nothing lazy crosses the boundary); ask
 * for the next one with [FactPage.next]. Facade over [ReadPosFacts].
 */
@Component
class PosReadModel(
    private val facts: ReadPosFacts,
) {
    fun bills(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID? = null,
        limit: Int = DEFAULT_PAGE,
    ): FactPage<BillFact> {
        val size = limit.coerceIn(1, ReadPosFacts.MAX_PAGE)
        val rows = facts.bills(venueId, businessDays, after, size)
        val items =
            rows.map { r ->
                BillFact(
                    billId = r.billId,
                    businessDay = r.businessDay,
                    paidAt = r.paidAt,
                    sessionStartedAt = r.sessionStartedAt,
                    totalMinor = r.totalMinor,
                    totalWithoutTaxMinor = r.totalWithoutTaxMinor,
                    discountMinor = r.discountMinor,
                    tipsMinor = r.tipsMinor,
                    refunded = r.refunded,
                    deleted = r.deleted,
                    deskId = r.deskId,
                    createdByStaffId = r.createdByStaffId,
                    paidByStaffId = r.paidByStaffId,
                    guestCount = r.guestCount,
                    modifiedAt = r.externalModifiedAt,
                    linesMissing = r.itemsUnreadable,
                    payments = r.payments.map { PaymentFact(it.kind, it.amountMinor) },
                )
            }
        return FactPage(items, items.lastOrNull()?.billId?.takeIf { items.size == size })
    }

    fun billLines(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID? = null,
        limit: Int = DEFAULT_PAGE,
    ): FactPage<BillLineFact> {
        val size = limit.coerceIn(1, ReadPosFacts.MAX_PAGE)
        val rows = facts.billLines(venueId, businessDays, after, size)
        val items =
            rows.map { r ->
                BillLineFact(
                    lineId = r.lineId,
                    billId = r.billId,
                    productId = r.productId,
                    quantity = r.quantity,
                    discountMinor = r.discountMinor,
                    totalMinor = r.totalMinor,
                    discounts =
                        r.discounts.map {
                            DiscountFact(
                                it.discountType,
                                DiscountAmount.of(it.absoluteMinor, it.percentValue, it.originalPriceMinor),
                            )
                        },
                )
            }
        return FactPage(items, items.lastOrNull()?.lineId?.takeIf { items.size == size })
    }

    /** Every venue the POS sync has linked, also ones it no longer syncs (their history stays). */
    fun syncedVenueIds(): List<UUID> = facts.syncedVenueIds()

    /** The business days of [venueId] that have bills. */
    fun businessDays(venueId: UUID): List<LocalDate> = facts.businessDays(venueId)

    /** What the venue's figures are missing, so a screen can say so (rule 2). */
    fun dataGaps(
        venueId: UUID,
        day: LocalDate? = null,
    ): DataGaps = facts.dataGaps(venueId, day).let { DataGaps(it.quarantinedBills, it.billsWithoutLines, it.outdatedBills) }

    /** When the venue's bills last synced successfully; `null` before the first sync (no data yet, rule 2). */
    fun lastBillsSyncAt(venueId: UUID): Instant? = facts.lastBillsSyncAt(venueId)

    companion object {
        const val DEFAULT_PAGE = 500
    }
}
