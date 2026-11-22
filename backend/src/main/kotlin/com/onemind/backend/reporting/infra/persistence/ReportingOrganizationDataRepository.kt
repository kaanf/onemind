package com.onemind.backend.reporting.infra.persistence

import com.onemind.db.reporting.tables.references.DESK_CAPACITY
import com.onemind.db.reporting.tables.references.DESK_DAILY_SALES
import com.onemind.db.reporting.tables.references.DISCOUNT_DAILY
import com.onemind.db.reporting.tables.references.HOURLY_ACTIVITY
import com.onemind.db.reporting.tables.references.HOURLY_SALES
import com.onemind.db.reporting.tables.references.PRODUCT_HOURLY_SALES
import com.onemind.db.reporting.tables.references.ROLLUP_STATE
import com.onemind.db.reporting.tables.references.STAFF_DAILY_SALES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

/** Deletes every rollup of the given venues. */
@Repository
class ReportingOrganizationDataRepository(
    private val dsl: DSLContext,
) {
    fun deleteAll(venueIds: Collection<UUID>) {
        dsl.deleteFrom(HOURLY_SALES).where(HOURLY_SALES.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(HOURLY_ACTIVITY).where(HOURLY_ACTIVITY.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(PRODUCT_HOURLY_SALES).where(PRODUCT_HOURLY_SALES.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(STAFF_DAILY_SALES).where(STAFF_DAILY_SALES.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(DESK_DAILY_SALES).where(DESK_DAILY_SALES.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(DISCOUNT_DAILY).where(DISCOUNT_DAILY.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(DESK_CAPACITY).where(DESK_CAPACITY.VENUE_ID.`in`(venueIds)).execute()
        dsl.deleteFrom(ROLLUP_STATE).where(ROLLUP_STATE.VENUE_ID.`in`(venueIds)).execute()
    }
}
