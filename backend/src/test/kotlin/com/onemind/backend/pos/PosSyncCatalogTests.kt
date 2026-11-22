package com.onemind.backend.pos

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.pos.application.RunPosSync
import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosBillPage
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosDeskView
import com.onemind.backend.pos.infra.client.PosMenu
import com.onemind.backend.pos.infra.client.PosMerchant
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosPlaceState
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
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
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    /**
     * The demo POS under any merchant ID starting with [PREFIX] (so every test has its own connection),
     * with knobs to change what it reports, and a check that no POS call runs inside a transaction.
     */
    class TestPosProvider(
        private val mock: MockPosProvider,
    ) : PosProvider {
        val callsInTransaction = mutableListOf<String>()
        val failing = mutableSetOf<String>()
        var droppedProduct: String? = null
        var droppedDesk: String? = null
        var renamedPlace: Pair<String, String>? = null
        var disabledPlace: String? = null

        private fun call(
            what: String,
            merchantId: String,
        ) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) callsInTransaction += what
            check(merchantId !in failing) { "POS unavailable" }
        }

        private fun PosPlaceRef.demo() = copy(merchantId = "demo-merchant")

        override fun merchant(merchantId: String): PosMerchant? {
            call("merchant", merchantId)
            if (!merchantId.startsWith(PREFIX)) return null
            val merchant = mock.merchant("demo-merchant") ?: return null
            return merchant.copy(
                merchantId = merchantId,
                places =
                    merchant.places.map { place ->
                        place.copy(
                            name = renamedPlace?.takeIf { it.first == place.placeId }?.second ?: place.name,
                            state = if (place.placeId == disabledPlace) PosPlaceState.DISABLED else place.state,
                        )
                    },
            )
        }

        override fun bills(
            place: PosPlaceRef,
            query: PosBillQuery,
        ): PosBillPage {
            call("bills", place.merchantId)
            return mock.bills(place.demo(), query)
        }

        override fun billDetail(
            place: PosPlaceRef,
            billId: String,
        ): PosBillDetail? {
            call("billDetail", place.merchantId)
            return mock.billDetail(place.demo(), billId)
        }

        override fun deskView(place: PosPlaceRef): PosDeskView {
            call("deskView", place.merchantId)
            val view = mock.deskView(place.demo())
            return view.copy(desks = view.desks.filterNot { it.deskId == droppedDesk })
        }

        override fun menu(place: PosPlaceRef): PosMenu {
            call("menu", place.merchantId)
            val menu = mock.menu(place.demo())
            return menu.copy(products = menu.products.filterNot { it.productId == droppedProduct })
        }

        fun reset() {
            callsInTransaction.clear()
            failing.clear()
            droppedProduct = null
            droppedDesk = null
            renamedPlace = null
            disabledPlace = null
        }
    }

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

    @AfterEach
    fun noPosCallInsideATransaction() {
        assertEquals(emptyList(), pos.callsInTransaction)
        pos.reset()
    }

    @Test
    fun `the first run creates a venue per place, linked, with menu and desks of the active ones`() {
        val connection = connection()

        sync.run()

        val venues = venues(connection)
        assertEquals(3, venues.size)
        assertTrue(venues.values.all { it.name.startsWith("Demo") && it.currency == "CZK" })
        assertEquals("DISABLED", venues.getValue("demo-oldtown").status)
        assertEquals("ACTIVE", venues.getValue("demo-letna").status)
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
        assertEquals(0, count("select count(*) from pos.product where venue_id = ?", venues.getValue("demo-oldtown").id))

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
        assertEquals(3, count("select count(*) from pos.venue_link where connection_id = ?", connection.id))
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
    fun `a renamed or disabled place updates its venue`() {
        val connection = connection()
        sync.run()
        pos.renamedPlace = "demo-letna" to "Demo · Bar Letná Garden"
        pos.disabledPlace = "demo-karlin"

        sync.run()

        val venues = venues(connection)
        assertEquals("Demo · Bar Letná Garden", venues.getValue("demo-letna").name)
        assertEquals("DISABLED", venues.getValue("demo-karlin").status)
        assertEquals(3, venues.size)
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
        assertEquals(3, venues(healthy).size)

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

    private fun connection(merchantId: String = "$PREFIX${UUID.randomUUID()}"): Connection {
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

    private companion object {
        const val PREFIX = "test-demo-"
    }
}
