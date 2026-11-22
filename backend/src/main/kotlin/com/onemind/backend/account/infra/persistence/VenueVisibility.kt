package com.onemind.backend.account.infra.persistence

import com.onemind.db.account.tables.references.MEMBERSHIP
import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.Condition
import java.util.UUID

/**
 * The one rule for which venues a user may open (AGENTS.md rule 7), over `VENUE` joined to its
 * `ORGANIZATION` and the user's `MEMBERSHIP` in it: the venue is active and the organization isn't
 * scheduled for deletion. Used by the tenancy check ([VenueAccessRepository.roleAt]) and the venue list
 * ([MyAccountRepository.venuesOf]), so the list never shows a venue the check would refuse.
 */
internal fun venueOpenTo(userId: UUID): Condition =
    VENUE.STATUS
        .eq(VENUE_ACTIVE)
        .and(MEMBERSHIP.USER_ID.eq(userId))
        .and(ORGANIZATION.DELETION_DUE_AT.isNull)

private const val VENUE_ACTIVE = "ACTIVE"
