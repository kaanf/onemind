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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /dashboard/sales` over synced demo bills (Friday 2026-11-20, 21:40 in Prague; data from 29 days
 * back). Expected values come from the `pos` tables with SQL, independent of the Kotlin calculation.
 */
@Import(TestcontainersConfiguration::class, SalesApiTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=29",
        "onemind.pos.sync.initial-days-per-run=40",
    ],
)
@AutoConfigureMockMvc
class SalesApiTests {
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
    fun `this week's figures equal an independent SQL calculation`() {
        val (owner, venue) = syncedOwner()

        val sales = get(owner, "period=WEEK", venue)

        assertEquals("READY", sales["status"].asString())
        assertEquals("2026-11-16", sales["range"]["from"].asString())
        assertEquals("2026-11-22", sales["range"]["to"].asString())
        assertFalse(sales["range"]["finished"].asBoolean())
        val week = "venue_id = '$venue' and business_day between '2026-11-16' and '2026-11-20'"
        val revenue = paid(week)
        assertEquals(revenue, sales["revenue"]["amountMinor"].asLong())
        assertEquals(sum("tips_minor", week), sales["tips"]["amountMinor"].asLong())
        assertEquals(sum("coalesce(guest_count, 1)", week), sales["guests"].asLong())
        assertEquals(halfUp(revenue, paidBills(week)), sales["averageBill"]["amountMinor"].asLong())
        val payments = sales["byPayment"]
        assertEquals(
            revenue,
            payments["card"]["amountMinor"].asLong() + payments["cash"]["amountMinor"].asLong() + payments["other"]["amountMinor"].asLong(),
        )
        assertEquals(listOf("TIPS_CARD_SHARE"), sales["unavailable"].toList().map { it.asString() })

        // Same elapsed span (M3 default): Mon–Thu last week whole, last Friday up to the same local time.
        val comparison = sales["comparison"]
        val previous =
            paid("venue_id = '$venue' and business_day between '2026-11-09' and '2026-11-12'") +
                paid("venue_id = '$venue' and business_day = '2026-11-13' and paid_at < timestamptz '2026-11-13T20:40:00Z'")
        assertEquals("2026-11-09", comparison["previous"]["from"].asString())
        assertEquals("2026-11-15", comparison["previous"]["to"].asString())
        assertEquals("2026-11-09", comparison["compared"]["from"].asString())
        assertEquals("2026-11-13", comparison["compared"]["to"].asString())
        assertTrue(comparison["upToSameTime"].asBoolean())
        assertEquals(previous, comparison["previousRevenue"]["amountMinor"].asLong())
        val delta = BigDecimal.valueOf((revenue - previous) * 100).divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
        assertEquals(delta.toPlainString(), comparison["deltaPercent"].asString())
    }

    @Test
    fun `the earlier side is cut at the local time of the last sync, not now`() {
        val (owner, venue) = syncedOwner()
        // The figures reach 21:10 Prague (last bills sync); it is 21:40 now.
        jdbc.update(
            "update pos.sync_cursor set last_success_at = timestamptz '2026-11-20T20:10:00Z' where venue_id = ? and resource = 'BILLS'",
            venue,
        )

        val sales = get(owner, "period=WEEK", venue)

        val previous =
            paid("venue_id = '$venue' and business_day between '2026-11-09' and '2026-11-12'") +
                paid("venue_id = '$venue' and business_day = '2026-11-13' and paid_at < timestamptz '2026-11-13T20:10:00Z'")
        assertEquals(previous, sales["comparison"]["previousRevenue"]["amountMinor"].asLong())
    }

    @Test
    fun `the current hour is compared with the same hour last week up to the same minute`() {
        val (owner, venue) = syncedOwner()

        val sales = get(owner, "period=HOUR", venue)

        assertEquals(21, sales["range"]["hour"].asInt())
        val hour = "venue_id = '$venue' and business_day = '2026-11-20' and extract(hour from paid_at at time zone 'Europe/Prague') = 21"
        assertEquals(paid(hour), sales["revenue"]["amountMinor"].asLong())
        val previous =
            paid(
                "venue_id = '$venue' and business_day = '2026-11-13' and " +
                    "paid_at >= timestamptz '2026-11-13T20:00:00Z' and paid_at < timestamptz '2026-11-13T20:40:00Z'",
            )
        assertEquals(previous, sales["comparison"]["previousRevenue"]["amountMinor"].asLong())
        assertEquals("2026-11-13", sales["comparison"]["compared"]["to"].asString())
    }

    @Test
    fun `a finished week is compared with the whole previous week`() {
        val (owner, venue) = syncedOwner()

        val sales = get(owner, "period=WEEK&date=2026-11-04", venue)

        assertTrue(sales["range"]["finished"].asBoolean())
        assertEquals(
            paid("venue_id = '$venue' and business_day between '2026-11-02' and '2026-11-08'"),
            sales["revenue"]["amountMinor"].asLong(),
        )
        val comparison = sales["comparison"]
        assertFalse(comparison["upToSameTime"].asBoolean())
        assertEquals("2026-10-26", comparison["compared"]["from"].asString())
        assertEquals("2026-11-01", comparison["compared"]["to"].asString())
        assertEquals(
            paid("venue_id = '$venue' and business_day between '2026-10-26' and '2026-11-01'"),
            comparison["previousRevenue"]["amountMinor"].asLong(),
        )
    }

    @Test
    fun `a venue whose data starts inside the compared span is left out of the comparison`() {
        val (owner, venue) = syncedOwner()

        // The data starts 29 days back, after 1 October: November can't be compared with October.
        val sales = get(owner, "period=MONTH", venue)

        assertEquals("2026-11-01", sales["range"]["from"].asString())
        assertEquals("2026-11-30", sales["range"]["to"].asString())
        assertEquals(
            paid("venue_id = '$venue' and business_day between '2026-11-01' and '2026-11-20'"),
            sales["revenue"]["amountMinor"].asLong(),
        )
        assertTrue(sales["comparison"].isNull)
    }

    @Test
    fun `a day stored by an older calculation gives no average bill and no payment split (rule 2)`() {
        val (owner, venue) = syncedOwner()
        jdbc.update("update reporting.rollup_state set rollup_version = 1 where venue_id = ? and business_day = '2026-11-18'", venue)

        val sales = get(owner, "period=WEEK", venue)

        assertTrue(sales["averageBill"].isNull && sales["byPayment"].isNull)
        assertFalse(sales["revenue"].isNull)
    }

    @Test
    fun `a venue still waiting for its first sync is pending, never counted as 0`() {
        val (owner, venue) = syncedOwner()
        val pending = newVenue(owner, "CZK")

        val both = get(owner, "period=DAY", venue, pending)
        val alone = get(owner, "period=DAY", pending)

        assertEquals(listOf(pending.toString()), both["pendingVenueIds"].toList().map { it.asString() })
        assertEquals(get(owner, "period=DAY", venue)["revenue"], both["revenue"])
        assertEquals("NO_DATA_YET", alone["status"].asString())
        assertTrue(alone["revenue"].isNull && alone["comparison"].isNull && alone["averageBill"].isNull)
    }

    @Test
    fun `a wrong period or date is a bad request`() {
        val (owner, venue) = syncedOwner()

        expectProblem(owner, "period=YEAR", venue, 400, "SALES_PERIOD_UNKNOWN")
        expectProblem(owner, "period=DAY&date=2026-11-21", venue, 400, "SALES_DATE_IN_FUTURE")
        expectProblem(owner, "period=HOUR&date=2026-11-19", venue, 400, "SALES_HOUR_NOT_TODAY")
        // Today itself is fine.
        expectProblem(owner, "period=DAY&date=2026-11-20", venue, 200, null)
    }

    @Test
    fun `another organization's venue is not found`() {
        TenancyFixture(jdbc, accessTokens).expectNoCrossVenueAccess { venueId, token ->
            mockMvc.get("/api/v1/dashboard/sales?venueIds=$venueId") { header("Authorization", "Bearer $token") }
        }
    }

    /** An owner whose demo organization is synced with its rollups refreshed; returns the owner and Letná. */
    private fun syncedOwner(): Pair<UUID, UUID> {
        val owner =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "sales-${UUID.randomUUID()}@example.com",
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

    private fun expectProblem(
        owner: UUID,
        query: String,
        venue: UUID,
        status: Int,
        code: String?,
    ) {
        mockMvc.get("/api/v1/dashboard/sales?venueIds=$venue&$query") { bearer(owner) }.andExpect {
            status { isEqualTo(status) }
            if (code != null) jsonPath("$.code") { value(code) }
        }
    }

    private fun get(
        owner: UUID,
        query: String,
        vararg venues: UUID,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get("/api/v1/dashboard/sales?venueIds=${venues.joinToString(",")}&$query") { bearer(owner) }
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

    /** Bills that count in `paidRevenue` (the demo data has no payment mismatches). */
    private fun paidBills(where: String): Long =
        requireNotNull(jdbc.queryForObject<Long>("select count(*) from pos.bill where $where and not deleted and not refunded"))

    private fun sum(
        expression: String,
        where: String,
    ): Long = requireNotNull(jdbc.queryForObject<Long>("select coalesce(sum($expression), 0) from pos.bill where $where and not deleted"))

    private fun halfUp(
        amount: Long,
        divisor: Long,
    ): Long = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).toLong()
}
