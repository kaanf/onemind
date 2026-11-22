package com.onemind.backend.account.infra.persistence

import com.onemind.db.account.tables.references.FOLLOWED_VENUE
import com.onemind.db.account.tables.references.MEMBERSHIP
import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.jooq.impl.DSL.inline
import org.springframework.stereotype.Repository
import java.util.UUID

data class MembershipRow(
    val organizationId: UUID,
    val role: String,
)

/** Organizations, memberships and followed venues as onboarding creates them. */
@Repository
class OrganizationRepository(
    private val dsl: DSLContext,
) {
    fun insertWithOwner(
        name: String,
        ownerId: UUID,
    ): UUID {
        val organizationId =
            dsl
                .insertInto(ORGANIZATION)
                .set(ORGANIZATION.NAME, name)
                .returning(ORGANIZATION.ID)
                .fetchOne(ORGANIZATION.ID)
                ?: error("insert returned no id")
        dsl
            .insertInto(MEMBERSHIP)
            .set(MEMBERSHIP.ORGANIZATION_ID, organizationId)
            .set(MEMBERSHIP.USER_ID, ownerId)
            .set(MEMBERSHIP.ROLE, OWNER)
            .execute()
        return organizationId
    }

    /**
     * The user's first membership (organization and role). Demo onboarding creates at most one per user
     * (it locks the user and checks this first); other memberships may come later with invitations.
     */
    fun membershipOf(userId: UUID): MembershipRow? =
        dsl
            .select(MEMBERSHIP.ORGANIZATION_ID, MEMBERSHIP.ROLE)
            .from(MEMBERSHIP)
            .where(MEMBERSHIP.USER_ID.eq(userId))
            .orderBy(MEMBERSHIP.ID)
            .limit(1)
            .fetchOne()
            ?.let { r -> MembershipRow(requireNotNull(r.get(MEMBERSHIP.ORGANIZATION_ID)), requireNotNull(r.get(MEMBERSHIP.ROLE))) }

    /** Follows those of [venueIds] that belong to an organization the user is a member of; already followed ones stay. */
    fun follow(
        userId: UUID,
        venueIds: Collection<UUID>,
    ) {
        if (venueIds.isEmpty()) return
        dsl
            .insertInto(FOLLOWED_VENUE, FOLLOWED_VENUE.USER_ID, FOLLOWED_VENUE.VENUE_ID)
            .select(
                dsl
                    .select(inline(userId), VENUE.ID)
                    .from(VENUE)
                    .join(MEMBERSHIP)
                    .on(MEMBERSHIP.ORGANIZATION_ID.eq(VENUE.ORGANIZATION_ID), MEMBERSHIP.USER_ID.eq(userId))
                    .where(VENUE.ID.`in`(venueIds)),
            ).onConflictDoNothing()
            .execute()
    }

    fun followedVenueIds(userId: UUID): List<UUID> =
        dsl
            .select(FOLLOWED_VENUE.VENUE_ID)
            .from(FOLLOWED_VENUE)
            .where(FOLLOWED_VENUE.USER_ID.eq(userId))
            .fetch(FOLLOWED_VENUE.VENUE_ID)
            .filterNotNull()

    private companion object {
        const val OWNER = "OWNER"
    }
}
