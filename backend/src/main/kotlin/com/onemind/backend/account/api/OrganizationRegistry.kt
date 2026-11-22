package com.onemind.backend.account.api

import com.onemind.backend.account.application.ManageOrganizations
import org.springframework.stereotype.Component
import java.util.UUID

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

    /** The organization [userId] is a member of, or `null` before onboarding. */
    fun organizationOf(userId: UUID): UUID? = organizations.organizationOf(userId)

    /** Follows [venueIds] of the user's own organizations; others are ignored. */
    fun follow(
        userId: UUID,
        venueIds: Collection<UUID>,
    ) = organizations.follow(userId, venueIds)

    fun followedVenueIds(userId: UUID): List<UUID> = organizations.followedVenueIds(userId)
}
