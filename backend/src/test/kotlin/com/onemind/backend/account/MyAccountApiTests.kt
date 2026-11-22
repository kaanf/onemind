package com.onemind.backend.account

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.common.security.AccessTokenIssuer
import com.onemind.backend.testing.TenancyFixture
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `GET /venues` and `GET /me`: only the caller's own organizations and venues, read per request. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class MyAccountApiTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    @Autowired
    lateinit var json: JsonMapper

    @Autowired
    lateinit var venueAccess: VenueAccess

    @Test
    fun `my venues are the active venues of my organizations, with followed and role`() {
        val fixture = TenancyFixture(jdbc, accessTokens)
        val second = venue(fixture.organizationA, "A second venue")
        val disabled = venue(fixture.organizationA, "Closed venue")
        fixture.disable(disabled)
        jdbc.update("insert into account.followed_venue (user_id, venue_id) values (?, ?)", fixture.ownerA, fixture.venueA)
        jdbc.update("update account.venue set business_day_start = '06:30', time_zone = 'Europe/Vienna' where id = ?", second)

        val venues = get("/api/v1/venues", fixture.token(fixture.ownerA))["venues"].toList()

        assertEquals(listOf(second.toString(), fixture.venueA.toString()), venues.map { it["id"].asString() })
        val (secondDto, venueA) = venues
        assertEquals("Europe/Vienna", secondDto["timeZone"].asString())
        assertEquals("06:30:00", secondDto["businessDayStart"].asString())
        assertEquals("CZK", secondDto["currency"].asString())
        assertEquals(false, secondDto["followed"].asBoolean())
        assertEquals(true, venueA["followed"].asBoolean())
        assertEquals("OWNER", venueA["role"].asString())
    }

    @Test
    fun `nobody sees another organization's venues`() {
        val fixture = TenancyFixture(jdbc, accessTokens)

        val staff = get("/api/v1/venues", fixture.token(fixture.staffA))["venues"].toList()
        val ownerB = get("/api/v1/venues", fixture.token(fixture.ownerB))["venues"].toList()
        val outsider = get("/api/v1/venues", fixture.token(fixture.outsider))["venues"].toList()

        assertEquals(listOf(fixture.venueA.toString() to "STAFF"), staff.map { it["id"].asString() to it["role"].asString() })
        assertEquals(listOf(fixture.venueB.toString()), ownerB.map { it["id"].asString() })
        assertTrue(outsider.isEmpty())
    }

    @Test
    fun `every listed venue passes the tenancy check, and every venue it refuses is left out`() {
        val fixture = TenancyFixture(jdbc, accessTokens)
        val disabled = venue(fixture.organizationA, "Closed venue")
        fixture.disable(disabled)
        val pendingDeletion = organization("Leaving")
        jdbc.update(
            "insert into account.membership (organization_id, user_id, role) values (?, ?, 'OWNER')",
            pendingDeletion,
            fixture.ownerA,
        )
        val leavingVenue = venue(pendingDeletion, "Leaving venue")
        fixture.scheduleDeletion(pendingDeletion)
        val candidates = listOf(fixture.venueA, fixture.venueB, disabled, leavingVenue)

        for (user in listOf(fixture.ownerA, fixture.staffA, fixture.ownerB, fixture.outsider)) {
            val listed = get("/api/v1/venues", fixture.token(user))["venues"].toList().map { UUID.fromString(it["id"].asString()) }.toSet()
            val allowed = candidates.filter { runCatching { venueAccess.requireAccess(user, it) }.isSuccess }.toSet()
            assertEquals(allowed, listed, "user $user")
        }
    }

    @Test
    fun `an organization scheduled for deletion drops out of my venues and memberships`() {
        val fixture = TenancyFixture(jdbc, accessTokens)
        fixture.scheduleDeletion(fixture.organizationA)

        assertTrue(get("/api/v1/venues", fixture.token(fixture.ownerA))["venues"].isEmpty)
        assertTrue(get("/api/v1/me", fixture.token(fixture.ownerA))["memberships"].isEmpty)
    }

    @Test
    fun `me is my profile and memberships, without invented initials`() {
        val fixture = TenancyFixture(jdbc, accessTokens)
        val token = fixture.token(fixture.ownerA)

        val me = get("/api/v1/me", token)

        assertEquals(fixture.ownerA.toString(), me["id"].asString())
        assertTrue(me["email"].asString().startsWith("owner-a-"))
        assertTrue(me["displayName"].isNull)
        assertEquals(
            listOf(Triple(fixture.organizationA.toString(), "Bar A", "OWNER")),
            me["memberships"].toList().map {
                Triple(
                    it["organizationId"].asString(),
                    it["organizationName"].asString(),
                    it["role"].asString(),
                )
            },
        )
        jdbc.update("update account.app_user set display_name = 'Kaan F' where id = ?", fixture.ownerA)
        assertEquals("Kaan F", get("/api/v1/me", token)["displayName"].asString())
    }

    @Test
    fun `a deleted user and a missing token are unauthorized`() {
        val fixture = TenancyFixture(jdbc, accessTokens)
        val token = fixture.token(fixture.outsider)
        mockMvc.delete("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/me") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        // Not an empty list: the app would read that as "not onboarded".
        mockMvc.get("/api/v1/venues") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/me").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/venues").andExpect { status { isUnauthorized() } }
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

    private fun get(
        path: String,
        token: String,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get(path) { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )
}
