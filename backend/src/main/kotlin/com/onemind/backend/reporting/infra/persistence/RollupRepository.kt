package com.onemind.backend.reporting.infra.persistence

import com.onemind.backend.reporting.domain.DayRollups
import com.onemind.db.reporting.tables.references.DESK_DAILY_SALES
import com.onemind.db.reporting.tables.references.DISCOUNT_DAILY
import com.onemind.db.reporting.tables.references.HOURLY_ACTIVITY
import com.onemind.db.reporting.tables.references.HOURLY_SALES
import com.onemind.db.reporting.tables.references.PRODUCT_HOURLY_SALES
import com.onemind.db.reporting.tables.references.ROLLUP_STATE
import com.onemind.db.reporting.tables.references.STAFF_DAILY_SALES
import org.jooq.DSLContext
import org.jooq.Query
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DSL.function
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.sum
import org.jooq.impl.DSL.value
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The rollups of a venue, replaced per business day (V004, V010): a refresh deletes a day's rows in every
 * day table and writes the new ones, so running it again gives the same rows.
 */
@Repository
class RollupRepository(
    private val dsl: DSLContext,
) {
    /**
     * Serialises refreshes of one (venue, day) until the transaction ends, so the last writer has read the
     * newest facts and two refreshes never interleave their deletes and inserts.
     */
    fun lockDay(
        venueId: UUID,
        day: LocalDate,
    ) {
        // A hash collision between two keys only makes their refreshes wait for each other.
        val key = function("hashtextextended", SQLDataType.BIGINT, value("rollup:$venueId:$day"), inline(0L))
        dsl.select(function("pg_advisory_xact_lock", SQLDataType.OTHER, key)).fetch()
    }

    fun replaceDay(
        venueId: UUID,
        day: LocalDate,
        rollups: DayRollups,
        sourceMaxModifiedAt: Instant?,
        version: Int,
        now: Instant,
    ) {
        dsl.deleteFrom(HOURLY_SALES).where(HOURLY_SALES.VENUE_ID.eq(venueId), HOURLY_SALES.BUSINESS_DAY.eq(day)).execute()
        dsl.deleteFrom(HOURLY_ACTIVITY).where(HOURLY_ACTIVITY.VENUE_ID.eq(venueId), HOURLY_ACTIVITY.BUSINESS_DAY.eq(day)).execute()
        dsl
            .deleteFrom(PRODUCT_HOURLY_SALES)
            .where(PRODUCT_HOURLY_SALES.VENUE_ID.eq(venueId), PRODUCT_HOURLY_SALES.BUSINESS_DAY.eq(day))
            .execute()
        dsl.deleteFrom(STAFF_DAILY_SALES).where(STAFF_DAILY_SALES.VENUE_ID.eq(venueId), STAFF_DAILY_SALES.BUSINESS_DAY.eq(day)).execute()
        dsl.deleteFrom(DESK_DAILY_SALES).where(DESK_DAILY_SALES.VENUE_ID.eq(venueId), DESK_DAILY_SALES.BUSINESS_DAY.eq(day)).execute()
        dsl.deleteFrom(DISCOUNT_DAILY).where(DISCOUNT_DAILY.VENUE_ID.eq(venueId), DISCOUNT_DAILY.BUSINESS_DAY.eq(day)).execute()
        val inserts = mutableListOf<Query>()
        rollups.hourlySales.forEach { r ->
            val t = HOURLY_SALES
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.HOUR, r.hour.toShort())
                    .set(t.REVENUE_MINOR, r.revenueMinor)
                    .set(t.REVENUE_WITHOUT_TAX_MINOR, r.revenueWithoutTaxMinor)
                    .set(t.BILLS_MISSING_NET, r.billsMissingNet)
                    .set(t.BILLS_PAYMENT_MISMATCH, r.billsPaymentMismatch)
                    .set(t.BILLS, r.bills)
                    .set(t.GUESTS, r.guests)
                    .set(t.TIPS_MINOR, r.tipsMinor)
                    .set(t.DISCOUNT_MINOR, r.discountMinor)
                    .set(t.CARD_MINOR, r.cardMinor)
                    .set(t.CASH_MINOR, r.cashMinor)
                    .set(t.OTHER_MINOR, r.otherMinor)
                    .set(t.REFUNDED_MINOR, r.refundedMinor)
                    .set(t.DELETED_BILLS, r.deletedBills)
                    .set(t.DELETED_MINOR, r.deletedMinor)
        }
        rollups.hourlyActivity.forEach { r ->
            val t = HOURLY_ACTIVITY
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.HOUR, r.hour.toShort())
                    .set(t.SESSIONS, r.sessions)
                    .set(t.GUESTS, r.guests)
        }
        rollups.productHourly.forEach { r ->
            val t = PRODUCT_HOURLY_SALES
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.HOUR, r.hour.toShort())
                    .set(t.PRODUCT_ID, r.productId)
                    .set(t.QUANTITY, r.quantity)
                    .set(t.GROSS_MINOR, r.grossMinor)
                    .set(t.DISCOUNT_MINOR, r.discountMinor)
        }
        rollups.staffDaily.forEach { r ->
            val t = STAFF_DAILY_SALES
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.STAFF_ID, r.staffId)
                    .set(t.BILLS, r.bills)
                    .set(t.REVENUE_MINOR, r.revenueMinor)
                    .set(t.GUESTS, r.guests)
                    .set(t.DISCOUNT_MINOR, r.discountMinor)
                    .set(t.DELETED_BILLS, r.deletedBills)
                    .set(t.TIPS_MINOR, r.tipsMinor)
        }
        rollups.deskDaily.forEach { r ->
            val t = DESK_DAILY_SALES
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.DESK_ID, r.deskId)
                    .set(t.BILLS, r.bills)
                    .set(t.REVENUE_MINOR, r.revenueMinor)
                    .set(t.GUESTS, r.guests)
                    .set(t.OCCUPIED_MINUTES, r.occupiedMinutes)
        }
        rollups.discountDaily.forEach { r ->
            val t = DISCOUNT_DAILY
            inserts +=
                dsl
                    .insertInto(t)
                    .set(t.VENUE_ID, venueId)
                    .set(t.BUSINESS_DAY, day)
                    .set(t.DISCOUNT_TYPE, r.discountType)
                    .set(t.RECORDS, r.records)
                    .set(t.AMOUNT_MINOR, r.amountMinor)
        }
        if (inserts.isNotEmpty()) dsl.batch(inserts).execute()

        val s = ROLLUP_STATE
        dsl
            .insertInto(s)
            .set(s.VENUE_ID, venueId)
            .set(s.BUSINESS_DAY, day)
            .set(s.REFRESHED_AT, now)
            .set(s.SOURCE_MAX_MODIFIED_AT, sourceMaxModifiedAt)
            .set(s.ROLLUP_VERSION, version)
            .onConflict(s.VENUE_ID, s.BUSINESS_DAY)
            .doUpdate()
            .set(s.REFRESHED_AT, excluded(s.REFRESHED_AT))
            .set(s.SOURCE_MAX_MODIFIED_AT, excluded(s.SOURCE_MAX_MODIFIED_AT))
            .set(s.ROLLUP_VERSION, excluded(s.ROLLUP_VERSION))
            .execute()
    }

    /** Bills of [venueId] left out of revenue because their payments don't add up, over all stored days. */
    fun paymentMismatches(venueId: UUID): Int =
        dsl
            .select(sum(HOURLY_SALES.BILLS_PAYMENT_MISMATCH))
            .from(HOURLY_SALES)
            .where(HOURLY_SALES.VENUE_ID.eq(venueId))
            .fetchOne(0, Int::class.java) ?: 0

    /** Of [days], the ones with no rollups yet or rollups from a calculation older than [version]. */
    fun staleDays(
        venueId: UUID,
        days: Collection<LocalDate>,
        version: Int,
    ): List<LocalDate> {
        val s = ROLLUP_STATE
        val current =
            dsl
                .select(s.BUSINESS_DAY)
                .from(s)
                .where(s.VENUE_ID.eq(venueId), s.BUSINESS_DAY.`in`(days), s.ROLLUP_VERSION.ge(version))
                .fetch(s.BUSINESS_DAY)
                .filterNotNull()
                .toSet()
        return days.filterNot { it in current }.sorted()
    }
}
