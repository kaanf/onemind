package com.onemind.backend.account.infra.persistence

import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Organizations past their `deletion_due_at` and the account rows deleted with them. */
@Repository
class OrganizationPurgeRepository(
    private val dsl: DSLContext,
) {
    /** Up to [limit] organizations due at [now], oldest due first, except [excluded]. */
    fun findDue(
        now: Instant,
        limit: Int,
        excluded: Collection<UUID>,
    ): List<UUID> {
        val o = ORGANIZATION
        return dsl
            .select(o.ID)
            .from(o)
            .where(o.DELETION_DUE_AT.le(now), o.ID.notIn(excluded))
            .orderBy(o.DELETION_DUE_AT, o.ID)
            .limit(limit)
            .fetch(o.ID)
            .filterNotNull()
    }

    /**
     * Locks [organizationId] if it is still due at [now]; `false` if a support reversal cleared the mark,
     * it is already gone or another run holds it.
     */
    fun lockIfDue(
        organizationId: UUID,
        now: Instant,
    ): Boolean {
        val o = ORGANIZATION
        return dsl
            .select(o.ID)
            .from(o)
            .where(o.ID.eq(organizationId), o.DELETION_DUE_AT.le(now))
            .forUpdate()
            .skipLocked()
            .fetchOne() != null
    }

    fun venueIds(organizationId: UUID): List<UUID> =
        dsl
            .select(VENUE.ID)
            .from(VENUE)
            .where(VENUE.ORGANIZATION_ID.eq(organizationId))
            .fetch(VENUE.ID)
            .filterNotNull()

    /** Deletes the venues (followed venues cascade) and the organization (memberships cascade). */
    fun deleteVenuesAndOrganization(organizationId: UUID) {
        dsl.deleteFrom(VENUE).where(VENUE.ORGANIZATION_ID.eq(organizationId)).execute()
        dsl.deleteFrom(ORGANIZATION).where(ORGANIZATION.ID.eq(organizationId)).execute()
    }
}
