package com.onemind.backend.pos.infra.persistence

import com.onemind.backend.pos.domain.StoredBill
import com.onemind.backend.pos.domain.StoredBillLine
import com.onemind.backend.pos.domain.StoredCategory
import com.onemind.backend.pos.domain.StoredDiscount
import com.onemind.backend.pos.domain.StoredPayment
import com.onemind.backend.pos.domain.StoredProduct
import com.onemind.backend.pos.domain.StoredStaff
import com.onemind.db.pos.tables.references.BILL
import com.onemind.db.pos.tables.references.BILL_ITEM
import com.onemind.db.pos.tables.references.BILL_ITEM_DISCOUNT
import com.onemind.db.pos.tables.references.BILL_QUARANTINE
import com.onemind.db.pos.tables.references.CATEGORY
import com.onemind.db.pos.tables.references.PAYMENT
import com.onemind.db.pos.tables.references.PRODUCT
import com.onemind.db.pos.tables.references.STAFF_MEMBER
import com.onemind.db.pos.tables.references.SYNC_CURSOR
import com.onemind.db.pos.tables.references.VENUE_LINK
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.noCondition
import org.jooq.impl.DSL.notExists
import org.jooq.impl.DSL.selectOne
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Read side of the POS read model for other modules (ADR 0013 §5, through `pos/api/PosReadModel`):
 * pages ordered by row ID (UUIDv7), continued after the last ID seen. Each call is complete when it
 * returns; nothing lazy crosses the module boundary.
 */
@Repository
class PosReadRepository(
    private val dsl: DSLContext,
) {
    fun bills(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID?,
        limit: Int,
    ): List<StoredBill> {
        val b = BILL
        val rows =
            dsl
                .selectFrom(b)
                .where(b.VENUE_ID.eq(venueId), b.BUSINESS_DAY.`in`(businessDays))
                .and(after?.let { b.ID.gt(it) } ?: b.ID.isNotNull)
                .orderBy(b.ID)
                .limit(limit)
                .fetch()
                .toList()
        val ids = rows.map { it.required(b.ID) }
        val payments =
            dsl
                .select(PAYMENT.BILL_ID, PAYMENT.METHOD_KIND, PAYMENT.AMOUNT_MINOR)
                .from(PAYMENT)
                .where(PAYMENT.BILL_ID.`in`(ids))
                .orderBy(PAYMENT.ID)
                .fetch()
                .toList()
                .groupBy(
                    { it.required(PAYMENT.BILL_ID) },
                    { StoredPayment(it.required(PAYMENT.METHOD_KIND), it.required(PAYMENT.AMOUNT_MINOR)) },
                )
        return rows.map { r ->
            val id = r.required(b.ID)
            StoredBill(
                billId = id,
                businessDay = r.required(b.BUSINESS_DAY),
                paidAt = r.required(b.PAID_AT),
                sessionStartedAt = r.get(b.SESSION_STARTED_AT),
                totalMinor = r.required(b.TOTAL_MINOR),
                totalWithoutTaxMinor = r.get(b.TOTAL_WITHOUT_TAX_MINOR),
                discountMinor = r.required(b.DISCOUNT_MINOR),
                tipsMinor = r.required(b.TIPS_MINOR),
                refunded = r.required(b.REFUNDED),
                deleted = r.required(b.DELETED),
                deskId = r.get(b.DESK_ID),
                createdByStaffId = r.get(b.CREATED_BY_STAFF_ID),
                paidByStaffId = r.get(b.PAID_BY_STAFF_ID),
                guestCount = r.get(b.GUEST_COUNT),
                externalModifiedAt = r.required(b.EXTERNAL_MODIFIED_AT),
                itemsUnreadable = r.required(b.ITEMS_UNREADABLE),
                payments = payments[id].orEmpty(),
            )
        }
    }

    fun billLines(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID?,
        limit: Int,
    ): List<StoredBillLine> {
        val i = BILL_ITEM
        val rows =
            dsl
                .select(i.ID, i.BILL_ID, i.PRODUCT_ID, i.QUANTITY, i.DISCOUNT_MINOR, i.TOTAL_MINOR)
                .from(i)
                .join(BILL)
                .on(BILL.ID.eq(i.BILL_ID))
                .where(i.VENUE_ID.eq(venueId), BILL.BUSINESS_DAY.`in`(businessDays))
                .and(after?.let { i.ID.gt(it) } ?: i.ID.isNotNull)
                .orderBy(i.ID)
                .limit(limit)
                .fetch()
                .toList()
        val ids = rows.map { it.required(i.ID) }
        val d = BILL_ITEM_DISCOUNT
        val discounts =
            dsl
                .select(d.BILL_ITEM_ID, d.DISCOUNT_TYPE, d.ABSOLUTE_MINOR, d.PERCENT_VALUE, d.ORIGINAL_PRICE_MINOR)
                .from(d)
                .where(d.BILL_ITEM_ID.`in`(ids))
                .orderBy(d.ID)
                .fetch()
                .toList()
                .groupBy(
                    { it.required(d.BILL_ITEM_ID) },
                    {
                        StoredDiscount(
                            it.get(d.DISCOUNT_TYPE),
                            it.get(d.ABSOLUTE_MINOR),
                            it.get(d.PERCENT_VALUE),
                            it.get(d.ORIGINAL_PRICE_MINOR),
                        )
                    },
                )
        return rows.map { r ->
            val id = r.required(i.ID)
            StoredBillLine(
                lineId = id,
                billId = r.required(i.BILL_ID),
                productId = r.get(i.PRODUCT_ID),
                quantity = r.required(i.QUANTITY),
                discountMinor = r.required(i.DISCOUNT_MINOR),
                totalMinor = r.required(i.TOTAL_MINOR),
                discounts = discounts[id].orEmpty(),
            )
        }
    }

    /** Every venue the sync has linked, also ones whose link is inactive now (their history stays). */
    fun syncedVenueIds(): List<UUID> =
        dsl
            .selectDistinct(VENUE_LINK.VENUE_ID)
            .from(VENUE_LINK)
            .orderBy(VENUE_LINK.VENUE_ID)
            .fetch(VENUE_LINK.VENUE_ID)
            .filterNotNull()

    /** The venue's products among [productIds], also ones no longer on the menu. */
    fun products(
        venueId: UUID,
        productIds: Collection<UUID>,
    ): List<StoredProduct> =
        dsl
            .select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.CATEGORY_ID)
            .from(PRODUCT)
            .where(PRODUCT.VENUE_ID.eq(venueId), PRODUCT.ID.`in`(productIds))
            .fetch()
            .toList()
            .map { r -> StoredProduct(requireNotNull(r.get(PRODUCT.ID)), requireNotNull(r.get(PRODUCT.NAME)), r.get(PRODUCT.CATEGORY_ID)) }

    /** The staff among [staffIds] of the POS account the venue is linked to (staff are per account, not per venue). */
    fun staff(
        venueId: UUID,
        staffIds: Collection<UUID>,
    ): List<StoredStaff> =
        dsl
            .select(STAFF_MEMBER.ID, STAFF_MEMBER.FULL_NAME, STAFF_MEMBER.USER_NAME)
            .from(STAFF_MEMBER)
            .join(VENUE_LINK)
            .on(VENUE_LINK.CONNECTION_ID.eq(STAFF_MEMBER.CONNECTION_ID))
            .where(VENUE_LINK.VENUE_ID.eq(venueId), STAFF_MEMBER.ID.`in`(staffIds))
            .fetch()
            .toList()
            .map { r -> StoredStaff(requireNotNull(r.get(STAFF_MEMBER.ID)), r.get(STAFF_MEMBER.FULL_NAME), r.get(STAFF_MEMBER.USER_NAME)) }

    /** Every category of the venue's menu (a small list). */
    fun categories(venueId: UUID): List<StoredCategory> =
        dsl
            .select(CATEGORY.ID, CATEGORY.NAME, CATEGORY.PARENT_ID)
            .from(CATEGORY)
            .where(CATEGORY.VENUE_ID.eq(venueId))
            .fetch()
            .toList()
            .map { r ->
                StoredCategory(requireNotNull(r.get(CATEGORY.ID)), requireNotNull(r.get(CATEGORY.NAME)), r.get(CATEGORY.PARENT_ID))
            }

    /** The business days of [venueId] that have bills. */
    fun businessDays(venueId: UUID): List<LocalDate> =
        dsl
            .selectDistinct(BILL.BUSINESS_DAY)
            .from(BILL)
            .where(BILL.VENUE_ID.eq(venueId))
            .orderBy(BILL.BUSINESS_DAY)
            .fetch(BILL.BUSINESS_DAY)
            .filterNotNull()

    /** Bills quarantined whole: not stored at all (a header-only bill is counted by [billsWithoutLines]). */
    fun quarantinedBills(venueId: UUID): Int {
        val q = BILL_QUARANTINE
        return dsl.fetchCount(
            q,
            q.VENUE_ID
                .eq(venueId)
                .and(notExists(selectOne().from(BILL).where(BILL.VENUE_ID.eq(q.VENUE_ID), BILL.EXTERNAL_ID.eq(q.EXTERNAL_ID)))),
        )
    }

    /**
     * Bills stored in an older version whose newer version couldn't be read at all: the stored figures
     * may be out of date (a later refund or deletion may be missing).
     */
    fun outdatedBills(
        venueId: UUID,
        days: ClosedRange<LocalDate>? = null,
    ): Int {
        val q = BILL_QUARANTINE
        return dsl.fetchCount(
            q,
            q.VENUE_ID.eq(venueId).and(
                exists(
                    selectOne()
                        .from(BILL)
                        .where(BILL.VENUE_ID.eq(q.VENUE_ID), BILL.EXTERNAL_ID.eq(q.EXTERNAL_ID), BILL.ITEMS_UNREADABLE.isFalse)
                        .and(days?.let { BILL.BUSINESS_DAY.between(it.start, it.endInclusive) } ?: noCondition()),
                ),
            ),
        )
    }

    /**
     * Bills stored from their header only (no lines). Counted by bill row, so a bill both stored this
     * way and quarantined for its detail counts here, not twice.
     */
    fun billsWithoutLines(
        venueId: UUID,
        days: ClosedRange<LocalDate>? = null,
    ): Int =
        dsl.fetchCount(
            BILL,
            BILL.VENUE_ID
                .eq(venueId)
                .and(BILL.ITEMS_UNREADABLE.isTrue)
                .and(days?.let { BILL.BUSINESS_DAY.between(it.start, it.endInclusive) } ?: noCondition()),
        )

    /** When the venue's bills last synced successfully; `null` before the first sync. */
    fun lastBillsSyncAt(venueId: UUID): Instant? =
        dsl
            .select(SYNC_CURSOR.LAST_SUCCESS_AT)
            .from(SYNC_CURSOR)
            .where(SYNC_CURSOR.VENUE_ID.eq(venueId), SYNC_CURSOR.RESOURCE.eq(BILLS_RESOURCE))
            .fetchOne(SYNC_CURSOR.LAST_SUCCESS_AT)
}

private const val BILLS_RESOURCE = "BILLS"

private fun <T> Record.required(field: Field<T?>): T = get(field) ?: error("${field.name} is null")
