package com.onemind.backend.reporting

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.pos.TestPosProvider
import com.onemind.backend.pos.api.events.BillsSynced
import com.onemind.backend.pos.application.RunPosSync
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.reporting.application.RebuildRollups
import com.onemind.backend.reporting.application.RefreshRollups
import com.onemind.backend.reporting.application.RollupReads
import com.onemind.backend.reporting.domain.RollupVersion
import com.onemind.backend.testing.MutableClock
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rollups from synced demo bills (ADR 0013 §5): refreshed by `BillsSynced`, rebuilt by the job. The
 * expected values come straight from the `pos` tables with SQL (tests may use date functions; the main
 * code may not), so the Kotlin calculation and an independent one must agree.
 */
@Import(TestcontainersConfiguration::class, ReportingRollupTests.Config::class)
@SpringBootTest(
    properties = [
        "db-scheduler.enabled=false",
        "onemind.pos.provider=mock",
        "onemind.pos.sync.initial-days=3",
    ],
)
class ReportingRollupTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        // The autumn DST night (2026-10-24/25) is inside the 3-day window.
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2026-10-26T21:00:00Z"))

        @Bean
        @Primary
        fun testPosProvider(mock: MockPosProvider) = TestPosProvider(mock)
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sync: RunPosSync

    @Autowired
    lateinit var refresh: RefreshRollups

    @Autowired
    lateinit var rebuild: RebuildRollups

    @Autowired
    lateinit var reads: RollupReads

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @Autowired
    lateinit var transactions: TransactionTemplate

    @Test
    fun `synced bills arrive in the rollups, equal to an independent SQL calculation`() {
        val venue = syncedVenue()
        // Two bills whose payments don't add up: tips above the payments, and a total without payments.
        val (tipsTooHigh, unpaid) =
            jdbc.queryForList(
                "select id from pos.bill where venue_id = ? and not deleted and total_minor > 0 order by id limit 2",
                UUID::class.java,
                venue,
            )
        jdbc.update("update pos.bill set tips_minor = 1000000 where id = ?", tipsTooHigh)
        jdbc.update("delete from pos.payment where bill_id = ?", unpaid)
        refresh.refresh(venue, days(venue))

        // Revenue, bills, refunds and deletions per local paid hour (the DST repeat hour merges, as in Kotlin).
        val expected =
            jdbc.queryForList(
                """
                with b as (
                    select bill.*,
                           coalesce((select sum(p.amount_minor) from pos.payment p where p.bill_id = bill.id), 0) as paid,
                           (select count(*) from pos.payment p where p.bill_id = bill.id) as payments
                    from pos.bill where venue_id = ?
                ), m as (
                    select b.*, (not deleted and (tips_minor > paid or (total_minor > 0 and payments = 0))) as mismatch from b
                )
                select business_day, extract(hour from paid_at at time zone 'Europe/Prague')::int as hour,
                       coalesce(sum(paid - tips_minor) filter (where not deleted and not mismatch), 0) as revenue,
                       count(*) filter (where not deleted) as bills,
                       coalesce(sum(paid - tips_minor) filter (where refunded and not deleted and not mismatch), 0) as refunded,
                       count(*) filter (where deleted) as deleted_bills,
                       coalesce(sum(total_minor) filter (where deleted), 0) as deleted,
                       count(*) filter (where mismatch) as mismatch
                from m group by 1, 2 order by 1, 2
                """.trimIndent(),
                venue,
            )
        val actual =
            jdbc.queryForList(
                """
                select business_day, hour::int as hour, revenue_minor as revenue, bills, refunded_minor as refunded,
                       deleted_bills, deleted_minor as deleted, bills_payment_mismatch as mismatch
                from reporting.hourly_sales where venue_id = ? order by 1, 2
                """.trimIndent(),
                venue,
            )
        assertEquals(normalize(expected), normalize(actual))
        assertTrue(expected.size > 20)
        assertEquals(2, reads.dataGaps(venue).billsPaymentMismatch)

        val productQuantity =
            "select coalesce(sum(i.quantity), 0) from pos.bill_item i join pos.bill b on b.id = i.bill_id " +
                "where b.venue_id = ? and not b.deleted and i.product_id is not null"
        val soldInPos = requireNotNull(jdbc.queryForObject<BigDecimal>(productQuantity, venue))
        val soldInRollups =
            requireNotNull(
                jdbc.queryForObject<BigDecimal>(
                    "select coalesce(sum(quantity), 0) from reporting.product_hourly_sales where venue_id = ?",
                    venue,
                ),
            )
        assertEquals(0, soldInPos.compareTo(soldInRollups))
        assertEquals(
            count("select coalesce(sum(tips_minor), 0) from pos.bill where venue_id = ? and not deleted", venue).toLong(),
            jdbc.queryForObject<Long>("select coalesce(sum(tips_minor), 0) from reporting.staff_daily_sales where venue_id = ?", venue),
        )
        assertEquals(
            count("select count(distinct business_day) from pos.bill where venue_id = ?", venue),
            count("select count(*) from reporting.rollup_state where venue_id = ? and rollup_version = ?", venue, RollupVersion.CURRENT),
        )
    }

    @Test
    fun `the same event twice changes nothing`() {
        val venue = syncedVenue()
        val days = days(venue)
        val before = snapshot(venue)

        publish(BillsSynced(venue, days))
        publish(BillsSynced(venue, days))
        awaitRefreshedAfter(venue, Instant.now())

        assertEquals(before, snapshot(venue))
    }

    @Test
    fun `a bill that moves to another business day leaves the old day`() {
        val venue = syncedVenue()
        val (oldDay, newDay) = days(venue).let { it.first() to it.last() }
        val billId =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "select id from pos.bill where venue_id = ? and business_day = ? and not deleted order by id limit 1",
                    venue,
                    oldDay,
                ),
            )
        val total = requireNotNull(jdbc.queryForObject<Long>("select total_minor from pos.bill where id = ?", billId))
        val oldRevenue = revenue(venue, oldDay)
        val newRevenue = revenue(venue, newDay)
        jdbc.update("update pos.bill set business_day = ? where id = ?", newDay, billId)

        refresh.refresh(venue, listOf(oldDay, newDay))

        assertEquals(oldRevenue - total, revenue(venue, oldDay))
        assertEquals(newRevenue + total, revenue(venue, newDay))
    }

    @Test
    fun `the rebuild fills missing days and, after a version bump, recomputes everything within a few runs`() {
        val venue = syncedVenue()
        val days = days(venue)
        val before = snapshot(venue)
        jdbc.update("delete from reporting.hourly_sales where venue_id = ? and business_day = ?", venue, days.first())
        jdbc.update("delete from reporting.rollup_state where venue_id = ? and business_day = ?", venue, days.first())

        rebuild.run()

        assertEquals(before, snapshot(venue))

        // As if the calculation changed: every day is below the version in code.
        jdbc.update("update reporting.rollup_state set rollup_version = ? where venue_id = ?", RollupVersion.CURRENT - 1, venue)
        var runs = 0
        while (count(
                "select count(*) from reporting.rollup_state where venue_id = ? and rollup_version < ?",
                venue,
                RollupVersion.CURRENT,
            ) >
            0
        ) {
            rebuild.run(budget = 2)
            runs++
            assertTrue(runs <= days.size, "rebuild stuck after $runs runs")
        }
        assertEquals(before, snapshot(venue))
    }

    @Test
    fun `data gaps report bills that couldn't be read and bills stored without lines`() {
        val venue = syncedVenue()
        jdbc.update(
            "insert into pos.bill_quarantine (venue_id, external_id, reason, first_seen_at, last_attempt_at) values (?, 'LOST-1', 'BAD_CURRENCY', now(), now())",
            venue,
        )
        jdbc.update(
            "update pos.bill set items_unreadable = true where id = (select id from pos.bill where venue_id = ? order by id limit 1)",
            venue,
        )
        // A stored bill whose newer version couldn't be read at all.
        jdbc.update(
            """
            insert into pos.bill_quarantine (venue_id, external_id, reason, first_seen_at, last_attempt_at)
            select venue_id, external_id, 'BAD_CURRENCY', now(), now() from pos.bill
            where venue_id = ? and not items_unreadable order by id desc limit 1
            """.trimIndent(),
            venue,
        )

        val gaps = reads.dataGaps(venue)

        assertEquals(1, gaps.quarantinedBills)
        assertEquals(1, gaps.billsWithoutLines)
        assertEquals(1, gaps.outdatedBills)
    }

    @Test
    fun `concurrent refreshes of the same day don't clash and end with the newest facts`() {
        val venue = syncedVenue()
        val day = days(venue).last()
        repeat(5) { round ->
            // New facts before every round: one payment grows by a crown.
            jdbc.update(
                """
                update pos.payment set amount_minor = amount_minor + 100 where id = (
                    select p.id from pos.payment p join pos.bill b on b.id = p.bill_id
                    where b.venue_id = ? and b.business_day = ? and not b.deleted order by p.id limit 1)
                """.trimIndent(),
                venue,
                day,
            )
            val failures = ConcurrentLinkedQueue<Throwable>()
            val start = CountDownLatch(1)
            val threads =
                (1..2).map {
                    thread {
                        start.await()
                        runCatching { refresh.refresh(venue, listOf(day)) }.onFailure { failures += it }
                    }
                }
            start.countDown()
            threads.forEach { it.join() }
            assertEquals(emptyList(), failures.toList(), "round $round")
            assertEquals(expectedRevenue(venue, day), revenue(venue, day), "round $round")
        }
    }

    /** A fresh connection synced through the demo POS, with its rollups refreshed by the events. */
    private fun syncedVenue(): UUID {
        val organization = requireNotNull(jdbc.queryForObject<UUID>("insert into account.organization (name) values ('Demo') returning id"))
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
        await("rollups of every synced day") {
            count("select count(distinct business_day) from pos.bill where venue_id = ?", venue) ==
                count("select count(*) from reporting.rollup_state where venue_id = ?", venue)
        }
        return venue
    }

    private fun publish(event: BillsSynced) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRefreshedAfter(
        venue: UUID,
        since: Instant,
    ) {
        Thread.sleep(500)
        await("refresh after $since") {
            count("select count(*) from reporting.rollup_state where venue_id = ? and refreshed_at is not null", venue) >
                0
        }
    }

    private fun await(
        what: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + 60_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            Thread.sleep(200)
        }
    }

    private fun days(venue: UUID): List<LocalDate> =
        jdbc
            .queryForList(
                "select distinct business_day from pos.bill where venue_id = ? order by 1",
                LocalDate::class.java,
                venue,
            ).filterNotNull()

    private fun expectedRevenue(
        venue: UUID,
        day: LocalDate,
    ): Long =
        requireNotNull(
            jdbc.queryForObject<Long>(
                """
                select coalesce(sum((select sum(p.amount_minor) from pos.payment p where p.bill_id = b.id) - b.tips_minor), 0)
                from pos.bill b where b.venue_id = ? and b.business_day = ? and not b.deleted
                  and exists (select 1 from pos.payment p where p.bill_id = b.id)
                """.trimIndent(),
                venue,
                day,
            ),
        )

    private fun revenue(
        venue: UUID,
        day: LocalDate,
    ): Long =
        requireNotNull(
            jdbc.queryForObject<Long>(
                "select coalesce(sum(revenue_minor), 0) from reporting.hourly_sales where venue_id = ? and business_day = ?",
                venue,
                day,
            ),
        )

    /** Every rollup row of the venue, as text, in a stable order. */
    private fun snapshot(venue: UUID): List<String> =
        listOf(
            "hourly_sales",
            "hourly_activity",
            "product_hourly_sales",
            "staff_daily_sales",
            "desk_daily_sales",
            "discount_daily",
        ).flatMap { table ->
            jdbc
                .queryForList("select * from reporting.$table where venue_id = ?", venue)
                .map { row -> table + row.filterKeys { it != "created_at" }.toSortedMap().toString() }
        }.sorted()

    private fun normalize(rows: List<Map<String, Any?>>) =
        rows.map { row ->
            row.mapValues { (_, v) ->
                (v as? Number)?.toLong()
                    ?: v.toString()
            }
        }

    private fun count(
        sql: String,
        vararg args: Any,
    ): Int = requireNotNull(jdbc.queryForObject<Int>(sql, *args))
}
