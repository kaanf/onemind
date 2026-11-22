package com.onemind.backend.pos

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.events.BillsSynced
import com.onemind.backend.pos.application.RunPosSync
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosPlaceRef
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bills sync (STORYOUS.md, sync strategy) against the demo POS: a 7-day initial import over the
 * autumn DST night, resumable; then `modifiedSince` with deleted bills; `BillsSynced` only for writes.
 */
@Import(TestcontainersConfiguration::class, PosSyncBillsTests.Config::class)
@SpringBootTest(properties = ["db-scheduler.enabled=false", "onemind.pos.provider=mock", "onemind.pos.sync.initial-days=$DAYS"])
@RecordApplicationEvents
class PosSyncBillsTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-10-30T21:00:00Z"))

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
    lateinit var mock: MockPosProvider

    @Autowired
    lateinit var sync: RunPosSync

    @Autowired
    lateinit var events: ApplicationEvents

    private val start = Instant.parse("2026-10-30T21:00:00Z")
    private val prague = ZoneId.of("Europe/Prague")

    @AfterEach
    fun cleanUp() {
        assertEquals(emptyList(), pos.callsInTransaction)
        pos.reset()
        clock.now = start
    }

    @Test
    fun `the initial import brings every paid bill of the last days, deleted ones too, with lines and staff`() {
        val connection = connection()

        sync.run()

        val venue = venue(connection, "demo-letna")
        val expected = expectedBills()
        assertEquals(expected.size, count("select count(*) from pos.bill where venue_id = ?", venue))
        assertEquals(expected.count { it.deleted }, count("select count(*) from pos.bill where venue_id = ? and deleted", venue))
        assertTrue(expected.any { it.deleted })
        assertEquals(
            today().minusDays(DAYS.toLong()),
            jdbc.queryForObject<LocalDate>("select min(business_day) from pos.bill where venue_id = ?", venue),
        )
        assertEquals(today(), jdbc.queryForObject<LocalDate>("select max(business_day) from pos.bill where venue_id = ?", venue))
        assertEquals(6, count("select count(*) from pos.staff_member where connection_id = ?", connection))

        val sample = expected.filterNot { it.deleted }.take(50)
        sample.forEach { bill ->
            val detail = requireNotNull(mock.billDetail(LETNA, bill.billId))
            val billId =
                requireNotNull(
                    jdbc.queryForObject<UUID>("select id from pos.bill where venue_id = ? and external_id = ?", venue, bill.billId),
                )
            assertEquals(detail.items.size, count("select count(*) from pos.bill_item where bill_id = ?", billId))
            assertEquals(
                detail.items.sumOf {
                    it.discounts.size
                },
                count(
                    "select count(*) from pos.bill_item_discount d join pos.bill_item i on i.id = d.bill_item_id where i.bill_id = ?",
                    billId,
                ),
            )
            assertEquals(detail.bill.payments.size, count("select count(*) from pos.payment where bill_id = ?", billId))
            assertEquals(
                bill.finalPrice.replace(".", "").toLong(),
                jdbc.queryForObject<Long>("select sum(total_minor) from pos.bill_item where bill_id = ?", billId),
                bill.billId,
            )
        }
        assertEquals(0, count("select count(*) from pos.bill where venue_id = ? and desk_id is null and not deleted", venue))
        assertEquals(0, count("select count(*) from pos.bill_item where venue_id = ? and product_id is null", venue))
        assertEquals(0, count("select count(*) from pos.bill where venue_id = ? and created_by_staff_id is null", venue))
    }

    @Test
    fun `business days follow the payment time and the venue cut-off, across midnight and the DST night`() {
        val connection = connection()

        sync.run()

        val venue = venue(connection, "demo-letna")
        val wrongDay =
            """
            select count(*) from pos.bill
            where venue_id = ? and business_day <> ((paid_at at time zone 'Europe/Prague') - interval '5 hours')::date
            """.trimIndent()
        assertEquals(0, count(wrongDay, venue))
        assertTrue(
            count(
                "select count(*) from pos.bill where venue_id = ? and (paid_at at time zone 'Europe/Prague')::date > business_day",
                venue,
            ) >
                0,
        )
        val dstNight =
            "select count(*) from pos.bill where venue_id = ? and business_day = '2026-10-24' " +
                "and external_id in ('LT20261024900', 'LT20261024901')"
        assertEquals(2, count(dstNight, venue))
    }

    @Test
    fun `an interrupted import resumes at the day it stopped, without duplicates`() {
        val connection = connection()
        val stopAt = today().minusDays(3)
        pos.failDetailsFrom = stopAt

        sync.run()

        val venue = venue(connection, "demo-letna")
        assertEquals("ERROR", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection))
        assertTrue(cursor(venue).startsWith("INITIAL|$stopAt|"), cursor(venue))
        assertEquals(0, count("select count(*) from pos.bill where venue_id = ? and business_day >= ?", venue, stopAt))
        assertTrue(count("select count(*) from pos.bill where venue_id = ?", venue) > 0)

        pos.failDetailsFrom = null
        sync.run()

        assertEquals(expectedBills().size, count("select count(*) from pos.bill where venue_id = ?", venue))
        assertTrue(cursor(venue).startsWith("SINCE|"), cursor(venue))
        assertEquals("ACTIVE", jdbc.queryForObject<String>("select status from pos.pos_connection where id = ?", connection))
    }

    @Test
    fun `a run with nothing new writes nothing and publishes no BillsSynced`() {
        val connection = connection()
        sync.run()
        val venue = venue(connection, "demo-letna")
        assertTrue(events.stream(BillsSynced::class.java).anyMatch { it.venueId == venue })
        val updatedAt = jdbc.queryForObject<Instant>("select max(updated_at) from pos.bill where venue_id = ?", venue)
        events.clear()

        sync.run()

        assertEquals(0, events.stream(BillsSynced::class.java).filter { it.venueId == venue }.count())
        assertEquals(updatedAt, jdbc.queryForObject<Instant>("select max(updated_at) from pos.bill where venue_id = ?", venue))
    }

    @Test
    fun `later runs bring new bills, refunds and deletions, and tell which business days changed`() {
        val connection = connection()
        sync.run()
        val venue = venue(connection, "demo-letna")
        events.clear()

        clock.advance(Duration.ofDays(1))
        sync.run()

        val expected = expectedBills()
        assertEquals(expected.size, count("select count(*) from pos.bill where venue_id = ?", venue))
        val refunded = expected.filter { it.refunded }
        assertTrue(refunded.isNotEmpty())
        refunded.forEach { bill ->
            assertEquals(
                true,
                jdbc.queryForObject<Boolean>("select refunded from pos.bill where venue_id = ? and external_id = ?", venue, bill.billId),
            )
            val lines =
                count(
                    "select count(*) from pos.bill_item i join pos.bill b on b.id = i.bill_id where b.venue_id = ? and b.external_id = ?",
                    venue,
                    bill.billId,
                )
            assertEquals(requireNotNull(mock.billDetail(LETNA, bill.billId)).items.size, lines, "items replaced, not added")
        }
        val synced = events.stream(BillsSynced::class.java).filter { it.venueId == venue }.toList()
        assertTrue(synced.isNotEmpty())
        assertTrue(today() in synced.flatMap { it.businessDays })
        assertTrue(synced.all { it.businessDays == it.businessDays.distinct().sorted() })
    }

    private fun today() = BusinessDayCalendar.today(clock, prague, BusinessDayCalendar.DEFAULT_BUSINESS_DAY_START)

    /** What the POS has for Letná since the import window started, deleted bills included. */
    private fun expectedBills(): List<PosBill> {
        // The first imported day is fixed by the first run, which always starts at [start].
        val firstDay =
            BusinessDayCalendar
                .businessDate(
                    start,
                    prague,
                    BusinessDayCalendar.DEFAULT_BUSINESS_DAY_START,
                ).minusDays(DAYS.toLong())
        val from = BusinessDayCalendar.range(firstDay, prague, BusinessDayCalendar.DEFAULT_BUSINESS_DAY_START).start
        val bills = mutableListOf<PosBill>()
        var cursor: String? = null
        do {
            val page = mock.bills(LETNA, PosBillQuery(from = from, includeDeleted = true, limit = 1000, lastBillId = cursor))
            bills += page.bills
            cursor = page.nextPage
        } while (cursor != null)
        return bills
    }

    private fun connection(): UUID {
        val organization = requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Demo') returning id"))
        return requireNotNull(
            jdbc.queryForObject<UUID>(
                "insert into pos.pos_connection (organization_id, provider, external_merchant_id) values (?, 'STORYOUS', ?) returning id",
                organization,
                "${TestPosProvider.PREFIX}${UUID.randomUUID()}",
            ),
        )
    }

    private fun venue(
        connection: UUID,
        placeId: String,
    ): UUID =
        requireNotNull(
            jdbc.queryForObject<UUID>(
                "select venue_id from pos.venue_link where connection_id = ? and external_place_id = ?",
                connection,
                placeId,
            ),
        )

    private fun cursor(venue: UUID): String =
        requireNotNull(jdbc.queryForObject<String>("select cursor from pos.sync_cursor where venue_id = ? and resource = 'BILLS'", venue))

    private fun count(
        sql: String,
        vararg args: Any,
    ): Int = requireNotNull(jdbc.queryForObject<Int>(sql, *args))

    private companion object {
        val LETNA = PosPlaceRef("demo-merchant", "demo-letna")
    }
}

private const val DAYS = 7
