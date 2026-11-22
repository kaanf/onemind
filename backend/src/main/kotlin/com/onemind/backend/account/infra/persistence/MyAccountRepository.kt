package com.onemind.backend.account.infra.persistence

import com.onemind.db.account.tables.references.APP_USER
import com.onemind.db.account.tables.references.FOLLOWED_VENUE
import com.onemind.db.account.tables.references.MEMBERSHIP
import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalTime
import java.util.Currency
import java.util.UUID

data class MyVenueRow(
    val venueId: UUID,
    val name: String,
    val timeZone: String,
    val currency: Currency,
    val businessDayStart: LocalTime,
    val followed: Boolean,
    val role: String,
)

data class ProfileRow(
    val userId: UUID,
    val email: String,
    val displayName: String?,
)

data class MembershipSummaryRow(
    val organizationId: UUID,
    val organizationName: String,
    val role: String,
)

/** What the signed-in user sees of their own account: profile, memberships and the venues they may open. */
@Repository
class MyAccountRepository(
    private val dsl: DSLContext,
) {
    /**
     * The venues the user may open, by name: the same rule as [VenueAccessRepository.roleAt] (a member of
     * the organization, venue active, organization not scheduled for deletion).
     */
    fun venuesOf(userId: UUID): List<MyVenueRow> {
        val v = VENUE
        val o = ORGANIZATION
        val m = MEMBERSHIP
        val f = FOLLOWED_VENUE
        return dsl
            .select(v.ID, v.NAME, v.TIME_ZONE, v.CURRENCY, v.BUSINESS_DAY_START, f.VENUE_ID, m.ROLE)
            .from(v)
            .join(o)
            .on(o.ID.eq(v.ORGANIZATION_ID))
            .join(m)
            .on(m.ORGANIZATION_ID.eq(o.ID))
            .leftJoin(f)
            .on(f.VENUE_ID.eq(v.ID), f.USER_ID.eq(userId))
            .where(m.USER_ID.eq(userId), v.STATUS.eq(ACTIVE), o.DELETION_DUE_AT.isNull)
            .orderBy(v.NAME, v.ID)
            .fetch()
            .toList()
            .map { r ->
                MyVenueRow(
                    venueId = requireNotNull(r.get(v.ID)),
                    name = requireNotNull(r.get(v.NAME)),
                    timeZone = requireNotNull(r.get(v.TIME_ZONE)),
                    currency = requireNotNull(r.get(v.CURRENCY)),
                    businessDayStart = requireNotNull(r.get(v.BUSINESS_DAY_START)),
                    followed = r.get(f.VENUE_ID) != null,
                    role = requireNotNull(r.get(m.ROLE)),
                )
            }
    }

    fun profile(userId: UUID): ProfileRow? =
        dsl
            .select(APP_USER.ID, APP_USER.EMAIL, APP_USER.DISPLAY_NAME)
            .from(APP_USER)
            .where(APP_USER.ID.eq(userId))
            .fetchOne()
            ?.let { r ->
                ProfileRow(requireNotNull(r.get(APP_USER.ID)), requireNotNull(r.get(APP_USER.EMAIL)), r.get(APP_USER.DISPLAY_NAME))
            }

    /** The user's memberships, organizations scheduled for deletion left out. */
    fun memberships(userId: UUID): List<MembershipSummaryRow> =
        dsl
            .select(ORGANIZATION.ID, ORGANIZATION.NAME, MEMBERSHIP.ROLE)
            .from(MEMBERSHIP)
            .join(ORGANIZATION)
            .on(ORGANIZATION.ID.eq(MEMBERSHIP.ORGANIZATION_ID))
            .where(MEMBERSHIP.USER_ID.eq(userId), ORGANIZATION.DELETION_DUE_AT.isNull)
            .orderBy(ORGANIZATION.NAME, ORGANIZATION.ID)
            .fetch()
            .toList()
            .map { r ->
                MembershipSummaryRow(
                    requireNotNull(r.get(ORGANIZATION.ID)),
                    requireNotNull(r.get(ORGANIZATION.NAME)),
                    requireNotNull(r.get(MEMBERSHIP.ROLE)),
                )
            }

    private companion object {
        const val ACTIVE = "ACTIVE"
    }
}
