package com.onemind.backend.account.application

import com.onemind.backend.account.domain.MembershipRole
import com.onemind.backend.account.infra.persistence.OrganizationRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Creating an organization with its owner and following venues, as onboarding does; reached by `pos`
 * through [OrganizationRegistry][com.onemind.backend.account.api.OrganizationRegistry]. Runs in the
 * caller's transaction.
 */
@Service
class ManageOrganizations(
    private val organizations: OrganizationRepository,
) {
    fun createForOwner(
        ownerId: UUID,
        name: String,
    ): UUID = organizations.insertWithOwner(name, ownerId)

    fun membershipOf(userId: UUID): Pair<UUID, MembershipRole>? =
        organizations.membershipOf(userId)?.let { it.organizationId to MembershipRole.valueOf(it.role) }

    fun follow(
        userId: UUID,
        venueIds: Collection<UUID>,
    ) = organizations.follow(userId, venueIds)

    fun followedVenueIds(userId: UUID): List<UUID> = organizations.followedVenueIds(userId)
}
