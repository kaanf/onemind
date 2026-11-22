package com.onemind.backend.pos

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.pos.application.RunPosSync
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.pos.infra.persistence.PosConnectionRepository
import com.onemind.backend.testing.MutableClock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Places → venues and menu/desks sync (STORYOUS.md, sync strategy) against the demo POS. */
@Import(TestcontainersConfiguration::class, PosSyncCatalogTests.Config::class)
@SpringBootTest(properties = ["db-scheduler.enabled=false", "onemind.pos.provider=mock"])
class PosSyncCatalogTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-11-20T21:00:00Z"))

        @Bean
        @Primary
        fun testPosProvider(mock: MockPosProvider) = TestPosProvider(mock)
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var pos: TestPosProvider

    @Autowired
    lateinit var sync: RunPosSync

    @Autowired
    lateinit var venueRegistry: VenueRegistry

    @Autowired
    lateinit var connections: PosConnectionRepository

    @AfterEach
    fun noPosCallInsideATransaction() {
        assertEquals(emptyList(), pos.callsInTransaction)
        pos.reset()
    }

    @Test
    fun `the first run creates a venue per active place, linked, with its menu and desks`() {
        val connection = connection()

        sync.run()

        val venues = venues(connection)
        assertEquals(setOf("demo-letna", "demo-karlin"), venues.keys, "a place first seen disabled gets no venue")
        assertTrue(venues.values.all { it.name.startsWith("Demo") && it.currency == "CZK" && it.status == "ACTIVE" })
        assertEquals(
            "ACTIVE",
            jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection.id),
        )

        val letna = venues.getValue("demo-letna").id
        assertEquals(5, count("select count(*) from pos.category where venue_id = ?", letna))
        assertEquals(24, count("select count(*) from pos.product where venue_id = ? and active", letna))
        assertEquals(3, count("select count(*) from pos.section where venue_id = ?", letna))
        assertEquals(13, count("select count(*) from pos.desk where venue_id = ? and not removed", letna))
        assertEquals(1, count("select count(*) from pos.desk where venue_id = ? and is_virtual", letna))
        assertEquals(
            12,
            count("select count(*) from pos.desk d join pos.section s on s.id = d.section_id where d.venue_id = ?", letna),
        )
        assertEquals(
            6900L,
            jdbc.queryForObject<Long>("select price_minor from pos.product where venue_id = ? and external_id = 'demo-product-1'", letna),
        )
        assertEquals(
            "demo-cat-beer",
            jdbc.queryForObject<String>(
                "select c.external_id from pos.product p join pos.category c on c.id = p.category_id where p.venue_id = ? and p.external_id = 'demo-product-1'",
                letna,
            ),
        )

        val (status, steps) = lastJob(connection)
        assertEquals("SUCCEEDED", status)
        assertTrue("\"places\"" in steps && "menu:$letna" in steps && "desks:$letna" in steps, steps)
    }

    @Test
    fun `a second run adds nothing, and menu and desks wait an hour`() {
        val connection = connection()
        sync.run()
        val before = venues(connection)

        sync.run()

        assertEquals(before, venues(connection))
        assertEquals(2, count("select count(*) from pos.venue_link where connection_id = ?", connection.id))
        assertTrue("menu:" !in lastJob(connection).second)

        clock.advance(Duration.ofMinutes(61))
        sync.run()
        assertTrue("menu:" in lastJob(connection).second)
        assertEquals(24, count("select count(*) from pos.product where venue_id = ?", before.getValue("demo-letna").id))
    }

    @Test
    fun `products and desks the POS no longer reports are flagged, not deleted, and come back`() {
        val connection = connection()
        sync.run()
        val letna = venues(connection).getValue("demo-letna").id
        pos.droppedProduct = "demo-product-3"
        pos.droppedDesk = "demo-letna-t1"
        clock.advance(Duration.ofMinutes(61))

        sync.run()

        assertEquals(
            false,
            jdbc.queryForObject<Boolean>("select active from pos.product where venue_id = ? and external_id = 'demo-product-3'", letna),
        )
        assertEquals(
            true,
            jdbc.queryForObject<Boolean>("select removed from pos.desk where venue_id = ? and external_id = 'demo-letna-t1'", letna),
        )
        assertEquals(24, count("select count(*) from pos.product where venue_id = ?", letna))

        pos.droppedProduct = null
        pos.droppedDesk = null
        clock.advance(Duration.ofMinutes(61))
        sync.run()
        assertEquals(
            true,
            jdbc.queryForObject<Boolean>("select active from pos.product where venue_id = ? and external_id = 'demo-product-3'", letna),
        )
        assertEquals(
            false,
            jdbc.queryForObject<Boolean>("select removed from pos.desk where venue_id = ? and external_id = 'demo-letna-t1'", letna),
        )
    }

    @Test
    fun `a renamed place renames its venue`() {
        val connection = connection()
        sync.run()
        pos.renamedPlace = "demo-letna" to "Demo · Bar Letná Garden"

        sync.run()

        assertEquals("Demo · Bar Letná Garden", venues(connection).getValue("demo-letna").name)
    }

    @Test
    fun `a disabled or vanished place stops syncing but keeps its venue, and comes back when reported again`() {
        val connection = connection()
        sync.run()
        pos.disabledPlace = "demo-karlin"
        pos.hiddenPlace = "demo-letna"
        pos.placeCalls.clear()
        clock.advance(Duration.ofMinutes(61))

        sync.run()

        val venues = venues(connection)
        assertEquals(setOf("demo-letna", "demo-karlin"), venues.keys)
        assertTrue(venues.values.all { it.status == "ACTIVE" }, "the venue and its history stay readable")
        assertEquals(0, count("select count(*) from pos.venue_link where connection_id = ? and active", connection.id))
        assertEquals(emptyList(), pos.placeCalls, "no menu, desk or bill calls for inactive links")
        assertEquals("ACTIVE", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection.id))

        pos.disabledPlace = null
        pos.hiddenPlace = null
        sync.run()
        assertEquals(2, count("select count(*) from pos.venue_link where connection_id = ? and active", connection.id))
        assertTrue(pos.placeCalls.any { it == "menu:demo-karlin" })
    }

    @Test
    fun `a failing venue is a failed step, the connection and the other venues go on`() {
        val connection = connection()
        val next = connection()
        pos.failingMenuPlace = "demo-karlin"

        sync.run()

        assertEquals("ACTIVE", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection.id))
        val (status, steps) = lastJob(connection)
        assertEquals("FAILED", status)
        assertTrue("\"IllegalStateException\"" in steps, steps)
        val karlin = venues(connection).getValue("demo-karlin").id
        assertEquals(
            "IllegalStateException",
            jdbc.queryForObject<String>("select last_error from pos.sync_cursor where venue_id = ? and resource = 'MENU'", karlin),
        )
        assertEquals(24, count("select count(*) from pos.product where venue_id = ?", venues(connection).getValue("demo-letna").id))
        // The next connection (same demo menus, so its Karlín fails too) still syncs.
        assertEquals(24, count("select count(*) from pos.product where venue_id = ?", venues(next).getValue("demo-letna").id))
        assertEquals(
            0,
            count("select count(*) from pos.sync_job where connection_id in (?, ?) and status = 'RUNNING'", connection.id, next.id),
        )
    }

    @Test
    fun `a failed menu waits an hour before it is fetched again`() {
        connection()
        pos.failingMenuPlace = "demo-karlin"
        sync.run()
        pos.placeCalls.clear()

        sync.run()
        assertTrue("menu:demo-karlin" !in pos.placeCalls)

        clock.advance(Duration.ofMinutes(61))
        sync.run()
        assertTrue("menu:demo-karlin" in pos.placeCalls)
    }

    @Test
    fun `a product with a sub-minor price is skipped, never rounded, and the rest of the menu is stored`() {
        val connection = connection()
        pos.subMinorPriceProduct = "demo-product-2"

        sync.run()

        val letna = venues(connection).getValue("demo-letna").id
        assertEquals(0, count("select count(*) from pos.product where venue_id = ? and external_id = 'demo-product-2'", letna))
        assertEquals(23, count("select count(*) from pos.product where venue_id = ?", letna))
        assertEquals(
            null,
            jdbc.queryForObject<String>("select last_error from pos.sync_cursor where venue_id = ? and resource = 'MENU'", letna),
        )
        assertEquals(
            1,
            count("select count(*) from pos.sync_cursor where venue_id = ? and resource = 'MENU' and last_success_at is not null", letna),
        )
    }

    @Test
    fun `a revoked connection stays revoked whatever a running sync reports`() {
        val connection = connection()
        jdbc.update("update pos.pos_connection set status = 'REVOKED' where id = ?", connection.id)

        connections.markSynced(connection.id, clock.now)
        connections.markFailed(connection.id, "IllegalStateException", clock.now)

        assertEquals("REVOKED", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection.id))
    }

    @Test
    fun `a failing connection is marked and doesn't stop the others`() {
        val broken = connection()
        val healthy = connection()
        pos.failing += broken.merchantId

        sync.run()

        assertEquals("ERROR", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", broken.id))
        assertEquals(
            "IllegalStateException",
            jdbc.queryForObject<String>("select last_error from pos.pos_connection where id = ?", broken.id),
        )
        assertEquals("FAILED", lastJob(broken).first)
        assertEquals(2, venues(healthy).size)

        pos.failing.clear()
        sync.run()
        assertEquals("ACTIVE", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", broken.id))
        assertEquals(null, jdbc.queryForObject<String>("select last_error from pos.pos_connection where id = ?", broken.id))
    }

    @Test
    fun `a merchant the POS doesn't know fails the connection`() {
        val unknown = connection(merchantId = "unknown-${UUID.randomUUID()}")

        sync.run()

        assertEquals(
            "PosMerchantNotFoundException",
            jdbc.queryForObject<String>("select last_error from pos.pos_connection where id = ?", unknown.id),
        )
        assertEquals(0, venues(unknown).size)
    }

    @Test
    fun `new venues keep the default zone and cut-off until the owner changes them`() {
        val connection = connection()
        sync.run()

        val settings = assertNotNull(venueRegistry.businessDay(venues(connection).getValue("demo-letna").id))
        assertEquals(ZoneId.of("Europe/Prague"), settings.zone)
        assertEquals(LocalTime.of(5, 0), settings.businessDayStart)
    }

    private data class Connection(
        val id: UUID,
        val organizationId: UUID,
        val merchantId: String,
    )

    private data class Venue(
        val id: UUID,
        val name: String,
        val status: String,
        val currency: String,
    )

    private fun connection(merchantId: String = "${TestPosProvider.PREFIX}${UUID.randomUUID()}"): Connection {
        val organization = requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Demo') returning id"))
        val id =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into pos.pos_connection (organization_id, provider, external_merchant_id) values (?, 'STORYOUS', ?) returning id",
                    organization,
                    merchantId,
                ),
            )
        return Connection(id, organization, merchantId)
    }

    /** The connection's venues by place ID. */
    private fun venues(connection: Connection): Map<String, Venue> =
        jdbc
            .query(
                """
                select l.external_place_id, v.id, v.name, v.status, v.currency, v.organization_id
                from pos.venue_link l join account.venue v on v.id = l.venue_id
                where l.connection_id = ?
                """.trimIndent(),
                { rs, _ ->
                    assertEquals(connection.organizationId, rs.getObject("organization_id", UUID::class.java))
                    rs.getString("external_place_id") to
                        Venue(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getString("status"), rs.getString("currency"))
                },
                connection.id,
            ).toMap()

    private fun lastJob(connection: Connection): Pair<String, String> =
        jdbc.queryForObject(
            "select status, steps::text from pos.sync_job where connection_id = ? order by started_at desc, created_at desc limit 1",
            { rs, _ -> rs.getString(1) to rs.getString(2) },
            connection.id,
        ) ?: error("no sync job")

    private fun count(
        sql: String,
        vararg args: Any,
    ): Int = requireNotNull(jdbc.queryForObject<Int>(sql, *args))
}
