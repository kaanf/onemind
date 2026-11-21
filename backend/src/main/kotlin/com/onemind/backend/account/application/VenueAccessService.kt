package com.onemind.backend.account.application

import com.onemind.backend.account.api.MembershipRole
import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.account.api.VenueNotFoundException
import com.onemind.backend.account.api.VenueRoleRequiredException
import com.onemind.backend.account.infra.VenueAccessRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VenueAccessService(
    private val venues: VenueAccessRepository,
) : VenueAccess {
    override fun requireAccess(
        userId: UUID,
        venueId: UUID,
        role: MembershipRole?,
    ): MembershipRole {
        val actual = venues.roleAt(userId, venueId)?.let(MembershipRole::valueOf) ?: throw VenueNotFoundException()
        if (role != null && actual < role) throw VenueRoleRequiredException(role)
        return actual
    }
}
