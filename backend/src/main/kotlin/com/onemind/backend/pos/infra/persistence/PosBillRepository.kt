package com.onemind.backend.pos.infra.persistence

import com.onemind.backend.pos.domain.SyncedBill
import com.onemind.backend.pos.domain.SyncedPerson
import com.onemind.db.pos.tables.references.BILL
import com.onemind.db.pos.tables.references.BILL_ITEM
import com.onemind.db.pos.tables.references.BILL_ITEM_DISCOUNT
import com.onemind.db.pos.tables.references.DESK
import com.onemind.db.pos.tables.references.PAYMENT
import com.onemind.db.pos.tables.references.PRODUCT
import com.onemind.db.pos.tables.references.STAFF_MEMBER
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.select
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Paid bills of a venue, upserted by `(venue_id, external_id)`. A bill whose POS modification time
 * hasn't changed is left alone; a changed one gets its items, discounts and payments replaced (Storyous
 * items have no stable ID, V003).
 */
@Repository
class PosBillRepository(
    private val dsl: DSLContext,
) {
    /** The stored POS modification time per external bill ID, for the given IDs. */
    fun modifiedAt(
        venueId: UUID,
        externalIds: Collection<String>,
    ): Map<String, Instant> =
        dsl
            .select(BILL.EXTERNAL_ID, BILL.EXTERNAL_MODIFIED_AT)
            .from(BILL)
            .where(BILL.VENUE_ID.eq(venueId), BILL.EXTERNAL_ID.`in`(externalIds))
            .fetch()
            .toList()
            .associate { r -> requireNotNull(r.get(BILL.EXTERNAL_ID)) to requireNotNull(r.get(BILL.EXTERNAL_MODIFIED_AT)) }

    /** Staff as the POS names them on bills, per connection; returns their row IDs by external ID. */
    fun upsertStaff(
        connectionId: UUID,
        people: Collection<SyncedPerson>,
        now: Instant,
    ): Map<String, UUID> {
        val s = STAFF_MEMBER
        return people.distinctBy { it.externalId }.associate { person ->
            person.externalId to
                requireNotNull(
                    dsl
                        .insertInto(s)
                        .set(s.CONNECTION_ID, connectionId)
                        .set(s.EXTERNAL_ID, person.externalId)
                        .set(s.FULL_NAME, person.fullName)
                        .set(s.USER_NAME, person.userName)
                        .onConflict(s.CONNECTION_ID, s.EXTERNAL_ID)
                        .doUpdate()
                        .set(s.FULL_NAME, excluded(s.FULL_NAME))
                        .set(s.USER_NAME, excluded(s.USER_NAME))
                        .set(s.UPDATED_AT, now)
                        .returning(s.ID)
                        .fetchOne(s.ID),
                )
        }
    }

    /**
     * Inserts or updates [bill] with its lines; `false` if it was stored with the same POS modification
     * time already, so nothing changed.
     */
    fun upsert(
        venueId: UUID,
        bill: SyncedBill,
        staff: Map<String, UUID>,
        now: Instant,
    ): Boolean {
        val b = BILL
        val deskId = field(select(DESK.ID).from(DESK).where(DESK.VENUE_ID.eq(venueId), DESK.EXTERNAL_ID.eq(bill.deskExternalId)))
        val billId =
            dsl
                .insertInto(b)
                .set(b.VENUE_ID, venueId)
                .set(b.EXTERNAL_ID, bill.externalId)
                .set(b.SESSION_STARTED_AT, bill.sessionStartedAt)
                .set(b.POS_CREATED_AT, bill.posCreatedAt)
                .set(b.PAID_AT, bill.paidAt)
                .set(b.FISCALIZED_AT, bill.fiscalizedAt)
                .set(b.BUSINESS_DAY, bill.businessDay)
                .set(b.TOTAL_MINOR, bill.totalMinor)
                .set(b.TOTAL_WITHOUT_TAX_MINOR, bill.totalWithoutTaxMinor)
                .set(b.DISCOUNT_MINOR, bill.discountMinor)
                .set(b.ROUNDING_MINOR, bill.roundingMinor)
                .set(b.TIPS_MINOR, bill.tipsMinor)
                .set(b.CURRENCY, bill.currency)
                .set(b.REFUNDED, bill.refunded)
                .set(b.DELETED, bill.deleted)
                .set(b.DESK_ID, deskId)
                .set(b.CREATED_BY_STAFF_ID, bill.createdBy?.let { staff[it.externalId] })
                .set(b.PAID_BY_STAFF_ID, bill.paidBy?.let { staff[it.externalId] })
                .set(b.GUEST_COUNT, bill.guestCount)
                .set(b.EXTERNAL_MODIFIED_AT, bill.externalModifiedAt)
                .onConflict(b.VENUE_ID, b.EXTERNAL_ID)
                .doUpdate()
                .set(b.SESSION_STARTED_AT, excluded(b.SESSION_STARTED_AT))
                .set(b.POS_CREATED_AT, excluded(b.POS_CREATED_AT))
                .set(b.PAID_AT, excluded(b.PAID_AT))
                .set(b.FISCALIZED_AT, excluded(b.FISCALIZED_AT))
                .set(b.BUSINESS_DAY, excluded(b.BUSINESS_DAY))
                .set(b.TOTAL_MINOR, excluded(b.TOTAL_MINOR))
                .set(b.TOTAL_WITHOUT_TAX_MINOR, excluded(b.TOTAL_WITHOUT_TAX_MINOR))
                .set(b.DISCOUNT_MINOR, excluded(b.DISCOUNT_MINOR))
                .set(b.ROUNDING_MINOR, excluded(b.ROUNDING_MINOR))
                .set(b.TIPS_MINOR, excluded(b.TIPS_MINOR))
                .set(b.CURRENCY, excluded(b.CURRENCY))
                .set(b.REFUNDED, excluded(b.REFUNDED))
                .set(b.DELETED, excluded(b.DELETED))
                .set(b.DESK_ID, excluded(b.DESK_ID))
                .set(b.CREATED_BY_STAFF_ID, excluded(b.CREATED_BY_STAFF_ID))
                .set(b.PAID_BY_STAFF_ID, excluded(b.PAID_BY_STAFF_ID))
                .set(b.GUEST_COUNT, excluded(b.GUEST_COUNT))
                .set(b.EXTERNAL_MODIFIED_AT, excluded(b.EXTERNAL_MODIFIED_AT))
                .set(b.UPDATED_AT, now)
                .where(b.EXTERNAL_MODIFIED_AT.isDistinctFrom(excluded(b.EXTERNAL_MODIFIED_AT)))
                .returning(b.ID)
                .fetchOne(b.ID)
                ?: return false
        replaceLines(billId, venueId, bill)
        return true
    }

    private fun replaceLines(
        billId: UUID,
        venueId: UUID,
        bill: SyncedBill,
    ) {
        // Discounts cascade with their items.
        dsl.deleteFrom(BILL_ITEM).where(BILL_ITEM.BILL_ID.eq(billId)).execute()
        dsl.deleteFrom(PAYMENT).where(PAYMENT.BILL_ID.eq(billId)).execute()
        val i = BILL_ITEM
        bill.items.forEach { item ->
            val productId =
                field(select(PRODUCT.ID).from(PRODUCT).where(PRODUCT.VENUE_ID.eq(venueId), PRODUCT.EXTERNAL_ID.eq(item.productExternalId)))
            val itemId =
                dsl
                    .insertInto(i)
                    .set(i.BILL_ID, billId)
                    .set(i.VENUE_ID, venueId)
                    .set(i.LINE_NO, item.lineNo)
                    .set(i.PRODUCT_ID, productId)
                    .set(i.EXTERNAL_PRODUCT_ID, item.productExternalId)
                    .set(i.NAME, item.name)
                    .set(i.QUANTITY, item.quantity)
                    .set(i.MEASURE, item.measure)
                    .set(i.UNIT_PRICE_MINOR, item.unitPriceMinor)
                    .set(i.VAT_RATE, item.vatRate)
                    .set(i.DISCOUNT_MINOR, item.discountMinor)
                    .set(i.TOTAL_MINOR, item.totalMinor)
                    .returning(i.ID)
                    .fetchOne(i.ID)
            if (item.discounts.isNotEmpty()) {
                val d = BILL_ITEM_DISCOUNT
                dsl
                    .batch(
                        item.discounts.map { discount ->
                            dsl
                                .insertInto(d)
                                .set(d.BILL_ITEM_ID, itemId)
                                .set(d.DISCOUNT_TYPE, discount.discountType)
                                .set(d.AMOUNT_TYPE, discount.amountType)
                                .set(d.PERCENT_VALUE, discount.percentValue)
                                .set(d.ABSOLUTE_MINOR, discount.absoluteMinor)
                                .set(d.ORIGINAL_PRICE_MINOR, discount.originalPriceMinor)
                                .set(d.NOTE, discount.note)
                        },
                    ).execute()
            }
        }
        if (bill.payments.isNotEmpty()) {
            val p = PAYMENT
            dsl
                .batch(
                    bill.payments.map { payment ->
                        dsl
                            .insertInto(p)
                            .set(p.BILL_ID, billId)
                            .set(p.METHOD_RAW, payment.methodRaw)
                            .set(p.METHOD_KIND, payment.methodKind.name)
                            .set(p.AMOUNT_MINOR, payment.amountMinor)
                    },
                ).execute()
        }
    }
}
