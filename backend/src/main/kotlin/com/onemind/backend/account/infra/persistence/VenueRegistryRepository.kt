package com.onemind.backend.account.infra.persistence

import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalTime
import java.util.Currency
import java.util.UUID

/** A venue's time zone and business-day cut-off, as stored (`venue.time_zone`, `venue.business_day_start`). */
data class VenueBusinessDayRow(
    val timeZone: String,
    val businessDayStart: LocalTime,
)

/** Venues created and kept up to date from what the POS reports. */
@Repository
class VenueRegistryRepository(
    private val dsl: DSLContext,
) {
    /** A new active venue with the default time zone and cut-off of the schema. */
    fun insert(
        organizationId: UUID,
        name: String,
        currency: Currency,
        addressLine: String?,
        city: String?,
    ): UUID =
        dsl
            .insertInto(VENUE)
            .set(VENUE.ORGANIZATION_ID, organizationId)
            .set(VENUE.NAME, name)
            .set(VENUE.CURRENCY, currency)
            .set(VENUE.ADDRESS_LINE, addressLine)
            .set(VENUE.CITY, city)
            .returning(VENUE.ID)
            .fetchOne(VENUE.ID)
            ?: error("insert returned no id")

    /** Name, address and status; `false` if the venue doesn't exist. Bumps `version` only on a change. */
    fun update(
        venueId: UUID,
        name: String,
        addressLine: String?,
        city: String?,
        status: String,
        now: Instant,
    ): Boolean {
        val v = VENUE
        val changed =
            v.NAME
                .isDistinctFrom(name)
                .or(v.ADDRESS_LINE.isDistinctFrom(addressLine))
                .or(v.CITY.isDistinctFrom(city))
                .or(v.STATUS.isDistinctFrom(status))
        val updated =
            dsl
                .update(v)
                .set(v.NAME, name)
                .set(v.ADDRESS_LINE, addressLine)
                .set(v.CITY, city)
                .set(v.STATUS, status)
                .set(v.VERSION, v.VERSION.plus(1))
                .set(v.UPDATED_AT, now)
                .where(v.ID.eq(venueId), changed)
                .execute()
        return updated > 0 || dsl.fetchExists(v, v.ID.eq(venueId))
    }

    fun businessDay(venueId: UUID): VenueBusinessDayRow? =
        dsl
            .select(VENUE.TIME_ZONE, VENUE.BUSINESS_DAY_START)
            .from(VENUE)
            .where(VENUE.ID.eq(venueId))
            .fetchOne()
            ?.let { r ->
                VenueBusinessDayRow(
                    timeZone = r.get(VENUE.TIME_ZONE) ?: error("time_zone is null"),
                    businessDayStart = r.get(VENUE.BUSINESS_DAY_START) ?: error("business_day_start is null"),
                )
            }
}
