package com.onemind.backend.account.infra

import com.onemind.db.account.tables.references.MEMBERSHIP
import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class VenueAccessRepository(
    private val dsl: DSLContext,
) {
    /**
     * The user's membership role in the organization of [venueId], or `null` if there is none, the venue
     * doesn't exist, is disabled or its organization is scheduled for deletion.
     */
    fun roleAt(
        userId: UUID,
        venueId: UUID,
    ): String? {
        val v = VENUE
        val o = ORGANIZATION
        val m = MEMBERSHIP
        return dsl
            .select(m.ROLE)
            .from(v)
            .join(o)
            .on(o.ID.eq(v.ORGANIZATION_ID))
            .join(m)
            .on(m.ORGANIZATION_ID.eq(o.ID))
            .where(v.ID.eq(venueId), v.STATUS.eq(ACTIVE), m.USER_ID.eq(userId), o.DELETION_DUE_AT.isNull)
            .fetchOne(m.ROLE)
    }

    private companion object {
        const val ACTIVE = "ACTIVE"
    }
}
