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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GET /dashboard/today` over synced demo bills (Friday 2026-11-20, 21:40 in Prague, with four earlier
 * Fridays). Expected values come from the `pos` tables with SQL, independent of the Kotlin calculation.
 */
@Import(TestcontainersConfiguration::class, TodayApiTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=29",
        "onemind.pos.sync.initial-days-per-run=40",
    ],
)
@AutoConfigureMockMvc
class TodayApiTests {
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

    @Test
    fun `today's figures equal an independent SQL calculation`() {
        val (owner, venue) = syncedOwner()

        val today = get(owner, venue)

        assertEquals("READY", today["status"].asString())
        assertEquals("2026-11-20", today["venues"][0]["businessDay"].asString())
        val todayBills = "venue_id = '$venue' and business_day = '2026-11-20'"
        assertEquals(paid(todayBills), today["paidSoFar"]["amountMinor"].asLong())
        assertEquals(sum("tips_minor", todayBills), today["tips"]["amountMinor"].asLong())
        assertEquals(sum("coalesce(guest_count, 1)", todayBills), today["guests"].asLong())

        val bars = today["hourly"]["hours"].associateBy { it["hour"].asInt() }
        assertEquals(21, today["hourly"]["currentHour"].asInt())
        assertEquals("CURRENT", bars.getValue(21)["kind"].asString())
        assertEquals(
            paid("$todayBills and extract(hour from paid_at at time zone 'Europe/Prague') = 21"),
            bars.getValue(21)["amount"]["amountMinor"].asLong(),
        )
        assertEquals(
            paid("$todayBills and extract(hour from paid_at at time zone 'Europe/Prague') = 19"),
            bars.getValue(19)["amount"]["amountMinor"].asLong(),
        )
        val earlierFridays = (1..4).map { "venue_id = '$venue' and business_day = date '2026-11-20' - ${7 * it}" }
        val expectedForecast =
            BigDecimal
                .valueOf(earlierFridays.sumOf { paid("$it and extract(hour from paid_at at time zone 'Europe/Prague') = 22") })
                .divide(BigDecimal(4), 0, RoundingMode.HALF_UP)
                .toLong()
        assertEquals("FORECAST", bars.getValue(22)["kind"].asString())
        assertEquals(expectedForecast, bars.getValue(22)["amount"]["amountMinor"].asLong())

        // Same weekday last week up to the same local time (no DST change in between: 7 × 24 h).
        val previous = paid("venue_id = '$venue' and business_day = '2026-11-13' and paid_at < timestamptz '2026-11-13T20:40:00Z'")
        assertEquals("2026-11-13", today["comparison"]["previousBusinessDay"].asString())
        assertEquals(previous, today["comparison"]["previous"]["amountMinor"].asLong())
        val delta = BigDecimal.valueOf((paid(todayBills) - previous) * 100).divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
        assertEquals(delta.toPlainString(), today["comparison"]["deltaPercent"].asString())
        assertEquals(
            listOf("OPEN_TABS", "OCCUPANCY_NOW", "STAFF_ON_SHIFT", "TIPS_CARD_SHARE"),
            today["unavailable"].toList().map { it.asString() },
        )
    }

    @Test
    fun `a venue still waiting for its first sync is pending, never counted as 0`() {
        val (owner, venue) = syncedOwner()
        val pending = newVenue(owner, "CZK")

        val both = get(owner, venue, pending)
        val alone = get(owner, pending)

        assertEquals("READY", both["status"].asString())
        assertEquals(listOf(pending.toString()), both["pendingVenueIds"].toList().map { it.asString() })
        assertEquals(get(owner, venue)["paidSoFar"], both["paidSoFar"])
        assertEquals("NO_DATA_YET", alone["status"].asString())
        assertTrue(alone["paidSoFar"].isNull && alone["hourly"].isNull && alone["comparison"].isNull)
    }

    @Test
    fun `an unchanged answer is 304 for its ETag`() {
        val (owner, venue) = syncedOwner()
        val first = mockMvc.get("/api/v1/dashboard/today?venueIds=$venue") { bearer(owner) }.andReturn().response
        val tag = requireNotNull(first.getHeader("ETag"))

        mockMvc
            .get("/api/v1/dashboard/today?venueIds=$venue") {
                bearer(owner)
                header("If-None-Match", tag)
            }.andExpect {
                status { isNotModified() }
                header { string("ETag", tag) }
            }
    }

    @Test
    fun `venues of different currencies are never added up`() {
        val (owner, venue) = syncedOwner()
        val inEuro = newVenue(owner, "EUR")

        mockMvc.get("/api/v1/dashboard/today?venueIds=$venue,$inEuro") { bearer(owner) }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("CURRENCIES_DIFFER") }
        }
    }

    @Test
    fun `another organization's venue is not found`() {
        TenancyFixture(jdbc, accessTokens).expectNoCrossVenueAccess { venueId, token ->
            mockMvc.get("/api/v1/dashboard/today?venueIds=$venueId") { header("Authorization", "Bearer $token") }
        }
    }

    @Test
    fun `the dashboard needs a signed-in user`() {
        mockMvc.get("/api/v1/dashboard/today?venueIds=${UUID.randomUUID()}").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/dashboard/today?venueIds=${UUID.randomUUID()}") { header("Authorization", "Bearer x") }.andExpect {
            status { isUnauthorized() }
        }
    }

    /** An owner whose demo organization is synced with its rollups refreshed; returns the owner and Letná. */
    private fun syncedOwner(): Pair<UUID, UUID> {
        val owner =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "today-${UUID.randomUUID()}@example.com",
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
        val deadline = System.nanoTime() + 120_000_000_000
        while (count("select count(distinct business_day) from pos.bill where venue_id = '$venue'") !=
            count("select count(*) from reporting.rollup_state where venue_id = '$venue'")
        ) {
            check(System.nanoTime() < deadline) { "rollups not refreshed" }
            Thread.sleep(250)
        }
        return owner to venue
    }

    private fun newVenue(
        owner: UUID,
        currency: String,
    ): UUID =
        requireNotNull(
            jdbc.queryForObject<UUID>(
                "insert into account.venue (organization_id, name, currency) select organization_id, 'Demo · New', ? from account.membership where user_id = ? returning id",
                currency,
                owner,
            ),
        )

    private fun get(
        owner: UUID,
        vararg venues: UUID,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get("/api/v1/dashboard/today?venueIds=${venues.joinToString(",")}") { bearer(owner) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(user: UUID) =
        header("Authorization", "Bearer ${accessTokens.issue(user).value}")

    /** `paidRevenue` of the bills matching [where]: payments without tips, not deleted, not refunded. */
    private fun paid(where: String): Long =
        requireNotNull(
            jdbc.queryForObject<Long>(
                """
                select coalesce(sum((select sum(p.amount_minor) from pos.payment p where p.bill_id = b.id) - b.tips_minor), 0)
                from pos.bill b where $where and not b.deleted and not b.refunded
                """.trimIndent(),
            ),
        )

    private fun sum(
        expression: String,
        where: String,
    ): Long = requireNotNull(jdbc.queryForObject<Long>("select coalesce(sum($expression), 0) from pos.bill where $where and not deleted"))

    private fun count(sql: String): Int = requireNotNull(jdbc.queryForObject<Int>(sql))
}
