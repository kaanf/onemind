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
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GET /dashboard/products` over synced demo bills (Friday 2026-11-20, 21:40 in Prague; data from 29 days
 * back). Expected values come from the `pos` tables with SQL, independent of the Kotlin calculation.
 */
@Import(TestcontainersConfiguration::class, ProductsApiTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=29",
        "onemind.pos.sync.initial-days-per-run=40",
    ],
)
@AutoConfigureMockMvc
class ProductsApiTests {
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
    fun `a day's products equal an independent SQL calculation`() {
        val (owner, venue) = syncedOwner()

        val products = get(owner, "date=2026-11-20", venue)

        assertEquals("READY", products["status"].asString())
        assertEquals("2026-11-20", products["businessDay"].asString())
        val lines =
            "from pos.bill_item i join pos.bill b on b.id = i.bill_id " +
                "where b.venue_id = '$venue' and b.business_day = '2026-11-20' and not b.deleted and i.product_id is not null"
        val rows = products["products"].toList()
        assertEquals(long("select count(distinct i.product_id) $lines"), rows.size.toLong())
        assertEquals(long("select coalesce(sum(i.total_minor), 0) $lines"), rows.sumOf { it["gross"]["amountMinor"].asLong() })
        // Largest gross first.
        val grosses = rows.map { it["gross"]["amountMinor"].asLong() }
        assertEquals(grosses.sortedDescending(), grosses)

        val top = rows.first()
        val product = "$lines and i.product_id = '${top["productId"].asString()}'"
        assertEquals(decimal("select sum(i.quantity) $product").stripTrailingZeros(), BigDecimal(top["sold"].asString()))
        val hour = "extract(hour from coalesce(b.session_started_at, b.paid_at) at time zone 'Europe/Prague')::int"
        val expectedHourly =
            jdbc.queryForList("select $hour as h, sum(i.quantity) as q $product group by 1").associate {
                (it["h"] as Number).toInt() to (it["q"] as BigDecimal).stripTrailingZeros()
            }
        val hourly = top["hourly"].toList().associate { it["hour"].asInt() to BigDecimal(it["quantity"].asString()) }
        assertEquals(expectedHourly, hourly)
        assertEquals(venue.toString(), top["venueId"].asString())
        assertTrue(products["categories"].toList().any { it["id"].asString() == top["categoryId"].asString() })
    }

    @Test
    fun `a day without sales is a real empty day, a venue without data is pending`() {
        val (owner, venue) = syncedOwner()
        val pending = newVenue(owner, "CZK")

        // The demo data starts 29 days back.
        val empty = get(owner, "date=2026-10-01", venue)
        val alone = get(owner, "date=2026-11-20", pending)

        assertEquals("READY", empty["status"].asString())
        assertTrue(empty["products"].isEmpty && empty["hours"].isEmpty && empty["categories"].isEmpty)
        assertEquals("NO_DATA_YET", alone["status"].asString())
        assertTrue(alone["products"].isNull && alone["hours"].isNull)
    }

    @Test
    fun `a future day is a bad request`() {
        val (owner, venue) = syncedOwner()

        mockMvc.get("/api/v1/dashboard/products?venueIds=$venue&date=2026-11-21") { bearer(owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("SALES_DATE_IN_FUTURE") }
        }
    }

    @Test
    fun `another organization's venue is not found`() {
        TenancyFixture(jdbc, accessTokens).expectNoCrossVenueAccess { venueId, token ->
            mockMvc.get("/api/v1/dashboard/products?venueIds=$venueId") { header("Authorization", "Bearer $token") }
        }
    }

    /** An owner whose demo organization is synced with its rollups refreshed; returns the owner and Letná. */
    private fun syncedOwner(): Pair<UUID, UUID> {
        val owner =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "products-${UUID.randomUUID()}@example.com",
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

    private fun get(
        owner: UUID,
        query: String,
        vararg venues: UUID,
    ): JsonNode =
        json.readTree(
            mockMvc
                .get("/api/v1/dashboard/products?venueIds=${venues.joinToString(",")}&$query") { bearer(owner) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer(user: UUID) =
        header("Authorization", "Bearer ${accessTokens.issue(user).value}")

    private fun long(sql: String): Long = requireNotNull(jdbc.queryForObject<Long>(sql))

    private fun decimal(sql: String): BigDecimal = requireNotNull(jdbc.queryForObject<BigDecimal>(sql))
}
