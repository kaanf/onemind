package com.onemind.backend.account.api

import com.onemind.backend.account.application.CheckVenueAccess
import com.onemind.backend.account.domain.MembershipRole
import org.springframework.stereotype.Component
import java.util.UUID

/** A user's role in a venue, as other modules see it; OWNER includes everything STAFF may do. */
enum class VenueRole { STAFF, OWNER }

/**
 * The tenancy check every venue-scoped use case calls first (AGENTS.md rule 7): `404 VENUE_NOT_FOUND`
 * for a venue that isn't the user's (or doesn't exist, or is disabled), `403 VENUE_ROLE_REQUIRED` for a
 * role that is too low. Facade over [CheckVenueAccess] (ADR 0013 §2). Pass no role (any member) or
 * [VenueRole.OWNER]; STAFF permissions wait for P2.
 */
@Component
class VenueAccess(
    private val checkVenueAccess: CheckVenueAccess,
) {
    /** The user's role in the venue; throws if they have no access or [role] is higher. */
    fun requireAccess(
        userId: UUID,
        venueId: UUID,
        role: VenueRole? = null,
    ): VenueRole = checkVenueAccess.check(userId, venueId, role?.toMembershipRole()).toVenueRole()

    private fun VenueRole.toMembershipRole() =
        when (this) {
            VenueRole.STAFF -> MembershipRole.STAFF
            VenueRole.OWNER -> MembershipRole.OWNER
        }

    private fun MembershipRole.toVenueRole() =
        when (this) {
            MembershipRole.STAFF -> VenueRole.STAFF
            MembershipRole.OWNER -> VenueRole.OWNER
        }
}
