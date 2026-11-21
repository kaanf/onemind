package com.onemind.backend.account.api

import com.onemind.backend.account.infra.VenueAccessRepository
import com.onemind.backend.common.error.ForbiddenException
import com.onemind.backend.common.error.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

/** A user's role in an organization (`account.membership.role`); OWNER includes everything STAFF may do. */
enum class MembershipRole { STAFF, OWNER }

class VenueNotFoundException :
    NotFoundException(
        code = "VENUE_NOT_FOUND",
        detail = "This venue doesn't exist or you don't have access to it.",
    )

class VenueRoleRequiredException(
    role: MembershipRole,
) : ForbiddenException(
        code = "VENUE_ROLE_REQUIRED",
        detail = "Your role in this venue doesn't allow this.",
        params = mapOf("role" to role.name),
    )

/**
 * The tenancy check every venue-scoped use case calls first (AGENTS.md rule 7, ADR 0010). Access comes
 * from the user's membership in the venue's organization, loaded per request, never from the token
 * (ADR 0004). A venue that doesn't exist, belongs to another organization or to one scheduled for
 * deletion is `404 VENUE_NOT_FOUND`, so other venues aren't revealed; a membership whose role is too
 * low is `403 VENUE_ROLE_REQUIRED`.
 *
 * What STAFF may do is open (docs/OPEN_QUESTIONS.md P2): until then use cases pass no role (any
 * member) or [MembershipRole.OWNER] for owner-only actions.
 */
@Service
class VenueAccess(
    private val venues: VenueAccessRepository,
) {
    /** The user's role in the venue's organization; throws if they have no access or [role] is higher. */
    fun requireAccess(
        userId: UUID,
        venueId: UUID,
        role: MembershipRole? = null,
    ): MembershipRole {
        val actual = venues.roleAt(userId, venueId)?.let(MembershipRole::valueOf) ?: throw VenueNotFoundException()
        if (role != null && actual < role) throw VenueRoleRequiredException(role)
        return actual
    }
}
