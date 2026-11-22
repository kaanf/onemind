package com.onemind.backend.account.api

import com.onemind.backend.account.application.ManageOrganizations
import com.onemind.backend.account.domain.MembershipRole
import org.springframework.stereotype.Component
import java.util.UUID

/** A user's organization and role, as onboarding sees it. */
data class OrganizationMembership(
    val organizationId: UUID,
    val role: VenueRole,
)

/**
 * The narrow way `pos` sets up an account during onboarding: an organization with the signed-in user as
 * its owner, and the venues they follow (A5). Nothing here joins a user to someone else's organization:
 * [follow] only follows venues of the user's own organizations. Facade over [ManageOrganizations]; calls
 * join the caller's transaction.
 */
@Component
class OrganizationRegistry(
    private val organizations: ManageOrganizations,
) {
    /** A new organization named [name] with [ownerId] as its owner. */
    fun createForOwner(
        ownerId: UUID,
        name: String,
    ): UUID = organizations.createForOwner(ownerId, name)

    /** The organization [userId] is a member of and their role in it, or `null` before onboarding. */
    fun membershipOf(userId: UUID): OrganizationMembership? =
        organizations.membershipOf(userId)?.let { (organizationId, role) ->
            OrganizationMembership(organizationId, if (role == MembershipRole.OWNER) VenueRole.OWNER else VenueRole.STAFF)
        }

    /** Follows [venueIds] of the user's own organizations; others are ignored. */
    fun follow(
        userId: UUID,
        venueIds: Collection<UUID>,
    ) = organizations.follow(userId, venueIds)

    fun followedVenueIds(userId: UUID): List<UUID> = organizations.followedVenueIds(userId)
}
