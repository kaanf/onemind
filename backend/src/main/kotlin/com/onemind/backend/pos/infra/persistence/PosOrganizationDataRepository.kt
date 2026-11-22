package com.onemind.backend.pos.infra.persistence

import com.onemind.db.pos.tables.references.BILL
import com.onemind.db.pos.tables.references.BILL_ITEM
import com.onemind.db.pos.tables.references.BILL_QUARANTINE
import com.onemind.db.pos.tables.references.CATEGORY
import com.onemind.db.pos.tables.references.DESK
import com.onemind.db.pos.tables.references.POS_CONNECTION
import com.onemind.db.pos.tables.references.PRODUCT
import com.onemind.db.pos.tables.references.RAW_PAYLOAD
import com.onemind.db.pos.tables.references.SECTION
import com.onemind.db.pos.tables.references.STAFF_MEMBER
import com.onemind.db.pos.tables.references.SYNC_CURSOR
import com.onemind.db.pos.tables.references.SYNC_JOB
import com.onemind.db.pos.tables.references.VENUE_LINK
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

/** Deletes the whole POS read model of an organization, in foreign-key order. */
@Repository
class PosOrganizationDataRepository(
    private val dsl: DSLContext,
) {
    fun deleteAll(
        organizationId: UUID,
        venueIds: Collection<UUID>,
    ) {
        // Items go by venue first (their discounts cascade), then bills (their payments cascade).
        dsl.deleteFrom(BILL_ITEM).where(BILL_ITEM.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(BILL).where(BILL.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(RAW_PAYLOAD).where(RAW_PAYLOAD.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(BILL_QUARANTINE).where(BILL_QUARANTINE.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(DESK).where(DESK.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(SECTION).where(SECTION.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(PRODUCT).where(PRODUCT.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(CATEGORY).where(CATEGORY.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(SYNC_CURSOR).where(SYNC_CURSOR.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(VENUE_LINK).where(VENUE_LINK.VENUE_ID.`in`(venueIds)).execute()

        val connections = dsl.select(POS_CONNECTION.ID).from(POS_CONNECTION).where(POS_CONNECTION.ORGANIZATION_ID.eq(organizationId))
        // Links of the connection, also to a venue outside venueIds: the connection goes, so they can't stay.
        dsl.deleteFrom(VENUE_LINK).where(VENUE_LINK.CONNECTION_ID.`in`(connections)).execute()
        dsl.deleteFrom(STAFF_MEMBER).where(STAFF_MEMBER.CONNECTION_ID.`in`(connections)).execute()
        dsl.deleteFrom(SYNC_JOB).where(SYNC_JOB.CONNECTION_ID.`in`(connections)).execute()
        dsl.deleteFrom(POS_CONNECTION).where(POS_CONNECTION.ORGANIZATION_ID.eq(organizationId)).execute()
    }
}
