package com.onemind.backend.testing

import com.onemind.backend.common.security.AccessTokenIssuer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.ResultActionsDsl
import java.util.UUID

/**
 * Two organizations with one venue each, and users in every position towards venue A: its owner,
 * a staff member, the owner of the other organization and a user without any membership. Build one
 * per test (all IDs are fresh), then call [expectNoCrossVenueAccess] for every venue-scoped endpoint.
 */
class TenancyFixture(
    private val jdbc: JdbcTemplate,
    private val accessTokens: AccessTokenIssuer,
) {
    val organizationA = organization("Bar A")
    val organizationB = organization("Bar B")
    val venueA = venue(organizationA, "Venue A")
    val venueB = venue(organizationB, "Venue B")
    val ownerA = user("owner-a", organizationA, "OWNER")
    val staffA = user("staff-a", organizationA, "STAFF")
    val ownerB = user("owner-b", organizationB, "OWNER")
    val outsider = user("outsider", organization = null, role = null)

    /** A valid access token of [userId]. */
    fun token(userId: UUID): String = accessTokens.issue(userId).value

    /** Marks [organization] for deletion, as when its last owner deletes their account. */
    fun scheduleDeletion(organization: UUID) {
        jdbc.update("update account.organization set deletion_due_at = now() + interval '30 days' where id = ?", organization)
    }

    private fun organization(name: String): UUID =
        requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values (?) returning id", name))

    private fun venue(
        organization: UUID,
        name: String,
    ): UUID =
        requireNotNull(
            jdbc.queryForObject<UUID>(
                "insert into account.venue (organization_id, name, currency) values (?, ?, 'CZK') returning id",
                organization,
                name,
            ),
        )

    private fun user(
        label: String,
        organization: UUID?,
        role: String?,
    ): UUID {
        val id =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email, email_verified_at) values (?, now()) returning id",
                    "$label-${UUID.randomUUID()}@example.com",
                ),
            )
        if (organization != null) {
            jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, ?)", organization, id, role)
        }
        return id
    }
}

/**
 * Rule 7 for one venue-scoped endpoint: [request] (venue ID, access token) succeeds for the owner of
 * venue A and answers `404 VENUE_NOT_FOUND` for the owner of another organization, a user without a
 * membership, and a venue that doesn't exist.
 */
fun TenancyFixture.expectNoCrossVenueAccess(request: (venueId: UUID, token: String) -> ResultActionsDsl) {
    request(venueA, token(ownerA)).andExpect { status { is2xxSuccessful() } }
    listOf(
        venueA to ownerB,
        venueA to outsider,
        UUID.randomUUID() to ownerA,
    ).forEach { (venue, user) ->
        request(venue, token(user)).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VENUE_NOT_FOUND") }
        }
    }
}
