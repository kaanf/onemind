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
 * `GET /dashboard/losses` over synced demo bills (Friday 2026-11-20, 21:40 in Prague; data from 29 days
 * back). Expected values come from the `pos` tables with SQL, independent of the Kotlin calculation.
 */
@Import(TestcontainersConfiguration::class, LossesApiTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=29",
        "onemind.pos.sync.initial-days-per-run=40",
    ],
)
@AutoConfigureMockMvc
class LossesApiTests {
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
    fun `this week's losses equal an independent SQL calculation`() {
        val (owner, venue) = syncedOwner()

        val losses = get(owner, "period=WEEK", venue)

        assertEquals("READY", losses["status"].asString())
        val week = "b.venue_id = '$venue' and b.business_day between '2026-11-16' and '2026-11-20'"
        val discounts = long("select coalesce(sum(b.discount_minor), 0) from pos.bill b where $week and not b.deleted")
        val refunds = revenue("$week and b.refunded and not b.deleted")
        val deleted = long("select coalesce(sum(b.total_minor), 0) from pos.bill b where $week and b.deleted")
        val components = losses["possibleLoss"]["components"].associate { it["kind"].asString() to it["amount"]["amountMinor"].asLong() }
        assertEquals(mapOf("DISCOUNTS" to discounts, "REFUNDS" to refunds, "DELETED_BILLS" to deleted), components)
        assertEquals(discounts + refunds + deleted, losses["possibleLoss"]["total"]["amountMinor"].asLong())

        val refundedBills = long("select count(*) from pos.bill b where $week and b.refunded and not b.deleted")
        val deletedBills = long("select count(*) from pos.bill b where $week and b.deleted")
        val discountRecords =
            long(
                "select count(*) from pos.bill_item_discount d join pos.bill_item i on i.id = d.bill_item_id join pos.bill b on b.id = i.bill_id where $week and not b.deleted",
            )
        assertEquals(refundedBills, losses["refunds"]["records"].asLong())
        assertEquals(deletedBills, losses["deletedBills"]["records"].asLong())
        assertEquals(discountRecords + refundedBills + deletedBills, losses["records"].asLong())

        // The by-type list adds up to the discounts component (UNTYPED takes the bill-level rest).
        assertEquals(0, losses["dataGaps"]["discountMismatchDayCount"].asInt())
        assertEquals(discounts, losses["discountsByType"].toList().sumOf { it["amount"]["amountMinor"].asLong() })

        val paid = revenue("$week and not b.refunded and not b.deleted")
        assertEquals(
            share(discounts + refunds + deleted, paid).setScale(1, RoundingMode.HALF_UP).toPlainString(),
            losses["sharePercent"].asString(),
        )
        val unavailable = losses["unavailableComponents"].toList().map { it.asString() }
        assertEquals(listOf("UNPAID_CLOSED", "ITEM_VOIDS", "STOCK_CORRECTIONS"), unavailable)
    }

    @Test
    fun `the loss share is compared in percentage points with the same elapsed span`() {
        val (owner, venue) = syncedOwner()

        val comparison = get(owner, "period=WEEK", venue)["comparison"]

        val week = "b.venue_id = '$venue' and b.business_day between '2026-11-16' and '2026-11-20'"
        val earlier =
            "b.venue_id = '$venue' and (b.business_day between '2026-11-09' and '2026-11-12' or " +
                "(b.business_day = '2026-11-13' and b.paid_at < timestamptz '2026-11-13T20:40:00Z'))"
        val current = share(loss(week), revenue("$week and not b.refunded and not b.deleted"))
        val previous = share(loss(earlier), revenue("$earlier and not b.refunded and not b.deleted"))
        assertEquals("2026-11-13", comparison["compared"]["to"].asString())
        assertEquals(previous.setScale(1, RoundingMode.HALF_UP).toPlainString(), comparison["previousSharePercent"].asString())
        assertEquals(current.subtract(previous).setScale(1, RoundingMode.HALF_UP).toPlainString(), comparison["changePoints"].asString())
    }

    @Test
    fun `record counts are hidden while a day is stored by an older calculation (rule 2)`() {
        val (owner, venue) = syncedOwner()
        jdbc.update("update reporting.rollup_state set rollup_version = 2 where venue_id = ? and business_day = '2026-11-18'", venue)

        val losses = get(owner, "period=WEEK", venue)

        assertTrue(losses["records"].isNull && losses["refunds"]["records"].isNull)
        assertTrue(losses["possibleLoss"]["total"]["amountMinor"].isNumber)
    }

    @Test
    fun `losses cover days, a venue without data is pending`() {
        val (owner, venue) = syncedOwner()
        val pending = newVenue(owner)

        mockMvc.get("/api/v1/dashboard/losses?venueIds=$venue&period=HOUR") { bearer(owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("LOSSES_HOUR_UNSUPPORTED") }
        }
        val alone = get(owner, "period=DAY", pending)
        assertEquals("NO_DATA_YET", alone["status"].asString())
        assertTrue(alone["possibleLoss"].isNull && alone["comparison"].isNull && alone["discountsByType"].isEmpty)
        assertTrue(alone["dataGaps"]["discountMismatchDayCount"].isNull)
    }

    @Test
    fun `another organization's venue is not found`() {
        TenancyFixture(jdbc, accessTokens).expectNoCrossVenueAccess { venueId, token ->
            mockMvc.get("/api/v1/dashboard/losses?venueIds=$venueId") { header("Authorization", "Bearer $token") }
        }
    }

    /** An owner whose demo organization is synced with its rollups refreshed; returns the owner and Letná. */
    private fun syncedOwner(): Pair<UUID, UUID> {
        val owner =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "losses-${UUID.randomUUID()}@example.com",
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
        while (long(pending) > 0) {
            check(System.nanoTime() < deadline) { "rollups not refreshed" }
            Thread.sleep(250)
        }
        return owner to venue
    }

    private fun newVenue(owner: UUID): UUID =
        requireNotNull(
            jdbc.queryForObject<UUID>(
                "insert into account.venue (organization_id, name, currency) select organization_id, 'Demo · New', 'CZK' from account.membership where user_id = ? returning id",
                owner,
            ),
        )

    private fun get(
        owner: UUID,
        query: String,
        vararg venues: UUID,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get("/api/v1/dashboard/losses?venueIds=${venues.joinToString(",")}&$query") { bearer(owner) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(user: UUID) =
        header("Authorization", "Bearer ${accessTokens.issue(user).value}")

    private fun long(sql: String): Long = requireNotNull(jdbc.queryForObject<Long>(sql))

    /** Payments without tips of the bills matching [where] (alias `b`). */
    private fun revenue(where: String): Long =
        long(
            "select coalesce(sum((select sum(p.amount_minor) from pos.payment p where p.bill_id = b.id) - b.tips_minor), 0) from pos.bill b where $where",
        )

    /** Possible loss of the bills matching [where]: bill-level discounts + refunds + whole deleted bills. */
    private fun loss(where: String): Long =
        long("select coalesce(sum(b.discount_minor), 0) from pos.bill b where $where and not b.deleted") +
            revenue("$where and b.refunded and not b.deleted") +
            long("select coalesce(sum(b.total_minor), 0) from pos.bill b where $where and b.deleted")

    private fun share(
        loss: Long,
        revenue: Long,
    ): BigDecimal = BigDecimal.valueOf(loss).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(revenue), 20, RoundingMode.HALF_UP)
}
