package com.onemind.backend.reporting

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.security.AccessTokenIssuer
import com.onemind.backend.pos.TestPosProvider
import com.onemind.backend.pos.application.RunPosSync
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.testing.MutableClock
import com.onemind.backend.testing.TenancyFixture
import com.onemind.backend.testing.expectNoCrossVenueAccess
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GET /dashboard/staff` over synced demo bills (Friday 2026-11-20, 21:40 in Prague; data from 29 days
 * back). Expected values come from the `pos` tables with SQL, independent of the Kotlin calculation.
 */
@Import(TestcontainersConfiguration::class, StaffApiTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=29",
        "onemind.pos.sync.initial-days-per-run=40",
    ],
)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffApiTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-11-20T20:40:00Z"))

        @Bean
        @Primary
        fun testPosProvider(mock: MockPosProvider) = TestPosProvider(mock)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    @Autowired
    lateinit var sync: RunPosSync

    @Autowired
    lateinit var json: JsonMapper

    /** One synced demo organization for all tests: they only read it, and the sync is the slow part. */
    private val shared by lazy { syncedOwner() }

    @Test
    fun `a day's staff figures equal an independent SQL calculation`() {
        val (owner, venue) = shared

        val staff = get(owner, "date=2026-11-19", venue)

        assertEquals("READY", staff["status"].asString())
        assertEquals("2026-11-19", staff["businessDay"].asString())
        val day = "b.venue_id = '$venue' and b.business_day = '2026-11-19'"
        val rows = staff["staff"].toList()
        assertEquals(
            long(
                "select count(distinct s) from (select created_by_staff_id s from pos.bill b where $day union select paid_by_staff_id from pos.bill b where $day and not b.deleted) x where s is not null",
            ),
            rows.size.toLong(),
        )
        assertEquals(
            long("select count(*) from pos.bill b where $day and not b.deleted and b.created_by_staff_id is not null"),
            staff["totals"]["billCount"].asLong(),
        )
        assertEquals(
            long("select coalesce(sum(b.tips_minor), 0) from pos.bill b where $day and not b.deleted and b.paid_by_staff_id is not null"),
            staff["totals"]["tips"]["amountMinor"].asLong(),
        )
        assertEquals(rows.sumOf { it["sales"]["amountMinor"].asLong() }, staff["totals"]["sales"]["amountMinor"].asLong())
        // Sales are paidRevenue: payments without tips of bills that aren't deleted or refunded; a bill whose
        // payments don't add up (tips above them, or a total without any) is left out.
        val payments = "(select coalesce(sum(p.amount_minor), 0) from pos.payment p where p.bill_id = b.id)"
        val paid =
            "select coalesce(sum($payments - b.tips_minor), 0) from pos.bill b where $day and not b.deleted and not b.refunded " +
                "and b.created_by_staff_id is not null and b.tips_minor <= $payments " +
                "and not (b.total_minor > 0 and not exists (select 1 from pos.payment p where p.bill_id = b.id))"
        assertEquals(long(paid), staff["totals"]["sales"]["amountMinor"].asLong())
        // Largest sales first.
        val sales = rows.map { it["sales"]["amountMinor"].asLong() }
        assertEquals(sales.sortedDescending(), sales)

        val top = rows.first()
        val person = top["staffId"].asString()
        assertEquals(
            long("select count(*) from pos.bill b where $day and not b.deleted and b.created_by_staff_id = '$person'"),
            top["billCount"].asLong(),
        )
        assertEquals(
            long("select count(*) from pos.bill b where $day and b.deleted and b.created_by_staff_id = '$person'"),
            top["deletedBillCount"].asLong(),
        )
        assertEquals(
            long(
                "select coalesce(sum(coalesce(b.guest_count, 1)), 0) from pos.bill b where $day and not b.deleted and b.created_by_staff_id = '$person'",
            ),
            top["guests"].asLong(),
        )
        assertEquals(
            jdbc.queryForObject<String>("select coalesce(full_name, user_name) from pos.staff_member where id = '$person'"),
            top["name"].asString(),
        )
    }

    @Test
    fun `a day before the data starts is no data, never a day without staff (rule 2)`() {
        val (owner, venue) = shared

        val beforeData = get(owner, "date=2026-10-01", venue)

        assertEquals("NO_DATA_YET", beforeData["status"].asString())
        assertTrue(beforeData["staff"].isNull && beforeData["totals"].isNull)
        assertEquals(listOf(venue.toString()), beforeData["pendingVenueIds"].toList().map { it.asString() })
    }

    @Test
    fun `a future day is a bad request`() {
        val (owner, venue) = shared

        mockMvc.get("/api/v1/dashboard/staff?venueIds=$venue&date=2026-11-21") { bearer(owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("SALES_DATE_IN_FUTURE") }
        }
    }

    @Test
    fun `another organization's venue is not found`() {
        TenancyFixture(jdbc, accessTokens).expectNoCrossVenueAccess { venueId, token ->
            mockMvc.get("/api/v1/dashboard/staff?venueIds=$venueId") { header("Authorization", "Bearer $token") }
        }
    }

    /** An owner whose demo organization is synced with its rollups refreshed; returns the owner and Letná. */
    private fun syncedOwner(): Pair<UUID, UUID> {
        val owner =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "staff-${UUID.randomUUID()}@example.com",
                ),
            )
        val organization = requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Demo') returning id"))
        jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, 'OWNER')", organization, owner)
        val connection =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into pos.pos_connection (organization_id, provider, external_merchant_id) values (?, 'STORYOUS', ?) returning id",
                    organization,
                    "${TestPosProvider.PREFIX}${UUID.randomUUID()}",
                ),
            )
        sync.run()
        val venue =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "select venue_id from pos.venue_link where connection_id = ? and external_place_id = 'demo-letna'",
                    connection,
                ),
            )
        // Rollups come asynchronously (BillsSynced listener): wait for every venue of the connection.
        val deadline = System.nanoTime() + 120_000_000_000
        val pending =
            "select count(*) from pos.venue_link l where l.connection_id = '$connection' and " +
                "(select count(distinct business_day) from pos.bill b where b.venue_id = l.venue_id) <> " +
                "(select count(*) from reporting.rollup_state r where r.venue_id = l.venue_id)"
        while (requireNotNull(jdbc.queryForObject<Long>(pending)) > 0) {
            check(System.nanoTime() < deadline) { "rollups not refreshed" }
            Thread.sleep(250)
        }
        return owner to venue
    }

    private fun get(
        owner: UUID,
        query: String,
        vararg venues: UUID,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get("/api/v1/dashboard/staff?venueIds=${venues.joinToString(",")}&$query") { bearer(owner) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(user: UUID) =
        header("Authorization", "Bearer ${accessTokens.issue(user).value}")

    private fun long(sql: String): Long = requireNotNull(jdbc.queryForObject<Long>(sql))
}
