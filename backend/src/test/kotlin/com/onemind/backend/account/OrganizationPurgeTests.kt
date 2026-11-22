package com.onemind.backend.account

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.account.application.PurgeDueOrganizations
import com.onemind.backend.testing.MutableClock
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The organization purge job (`docs/architecture/DB_SCHEMA.md`, account deletion) over a row in every
 * organization-scoped table. The scheduler is off here, so only the test runs the job; the clock stands far
 * in the future, so the scheduler of a context running on the system clock never sees these
 * organizations as due.
 */
@Import(TestcontainersConfiguration::class, OrganizationPurgeTests.Config::class)
@SpringBootTest(properties = ["db-scheduler.enabled=false"])
class OrganizationPurgeTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock(Instant.parse("2100-01-01T00:00:00Z"))
    }

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var purge: PurgeDueOrganizations

    @Test
    fun `a due organization goes with all its data, others stay untouched`() {
        val due = seed(dueAt = clock.now - Duration.ofMinutes(1))
        val notYetDue = seed(dueAt = clock.now + Duration.ofDays(1))
        val cleared = seed(dueAt = clock.now - Duration.ofDays(1)).also { clearDeletion(it) }
        val active = seed(dueAt = null)

        assertTrue(purge.run() >= 1, "older due organizations of other tests may go too")

        expectGone(due)
        expectIntact(notYetDue)
        expectIntact(cleared)
        expectIntact(active)
        assertEquals(1, count("select count(*) from account.app_user where id = ?", due.staffUserId), "members are users of their own")
    }

    @Test
    fun `a second run changes nothing`() {
        val due = seed(dueAt = clock.now - Duration.ofMinutes(1))
        val notYetDue = seed(dueAt = clock.now + Duration.ofDays(1))
        purge.run()

        purge.run()

        expectGone(due)
        expectIntact(notYetDue)
    }

    @Test
    fun `an organization that fails is rolled back whole and does not stop the others`() {
        val failing = seed(dueAt = clock.now - Duration.ofDays(2))
        val other = seed(dueAt = clock.now - Duration.ofDays(1))
        // A foreign venue linked to the failing organization's connection makes its POS delete fail.
        val foreignVenue = venue(seed(dueAt = null).organizationId)
        jdbc.update(
            "insert into pos.venue_link (venue_id, connection_id, external_place_id) values (?, ?, ?)",
            foreignVenue,
            failing.connectionId,
            "foreign-place",
        )

        purge.run()

        expectIntact(failing)
        expectGone(other)
    }

    @Test
    fun `every organization-scoped table is covered by these tests`() {
        val tables =
            jdbc
                .queryForList(
                    """
                    select table_schema || '.' || table_name from information_schema.tables
                    where table_type = 'BASE TABLE'
                      and table_schema not in ('pg_catalog', 'information_schema', 'public', 'platform')
                    """.trimIndent(),
                    String::class.java,
                ).toSet()
        assertEquals(tables - USER_SCOPED, SCOPES.keys, "a new table needs a row in seed() and a scope here")
    }

    private fun expectGone(org: Seeded) {
        SCOPES.forEach { (table, scope) -> assertEquals(0, countIn(table, scope, org), "$table of a purged organization") }
    }

    private fun expectIntact(org: Seeded) {
        SCOPES.forEach { (table, scope) -> assertEquals(1, countIn(table, scope, org), "$table of a kept organization") }
    }

    private fun countIn(
        table: String,
        scope: Scope,
        org: Seeded,
    ) = count("select count(*) from $table where ${scope.column} = ?", scope.id(org))

    private fun count(
        sql: String,
        vararg args: Any,
    ): Int = requireNotNull(jdbc.queryForObject<Int>(sql, *args))

    private fun clearDeletion(org: Seeded) {
        jdbc.update("update account.organization set deletion_due_at = null where id = ?", org.organizationId)
    }

    private data class Seeded(
        val organizationId: UUID,
        val venueId: UUID,
        val staffUserId: UUID,
        val connectionId: UUID,
        val billId: UUID,
        val billItemId: UUID,
    )

    private class Scope(
        val column: String,
        val id: (Seeded) -> UUID,
    )

    private fun id(
        sql: String,
        vararg args: Any?,
    ): UUID = requireNotNull(jdbc.queryForObject<UUID>(sql, *args))

    private fun venue(organizationId: UUID): UUID =
        id("insert into account.venue (organization_id, name, currency) values (?, 'Demo venue', 'CZK') returning id", organizationId)

    /** One row in every organization-scoped table, for a new organization due at [dueAt]. */
    private fun seed(dueAt: Instant?): Seeded {
        val ext = UUID.randomUUID().toString()
        val org =
            id(
                "insert into account.organization (name, deletion_due_at) values ('Demo bar', ?) returning id",
                dueAt?.let(Timestamp::from),
            )
        val venue = venue(org)
        val user = id("insert into account.app_user (email) values (?) returning id", "staff-$ext@example.com")
        jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, 'STAFF')", org, user)
        jdbc.update("insert into account.followed_venue (user_id, venue_id) values (?, ?)", user, venue)

        val connection =
            id(
                "insert into pos.pos_connection (organization_id, provider, external_merchant_id) values (?, 'STORYOUS', ?) returning id",
                org,
                ext,
            )
        jdbc.update("insert into pos.venue_link (venue_id, connection_id, external_place_id) values (?, ?, ?)", venue, connection, ext)
        jdbc.update("insert into pos.sync_cursor (venue_id, resource) values (?, 'BILLS')", venue)
        jdbc.update("insert into pos.sync_job (connection_id, kind) values (?, 'INITIAL')", connection)
        val category = id("insert into pos.category (venue_id, external_id, name) values (?, ?, 'Beer') returning id", venue, ext)
        val product =
            id(
                "insert into pos.product (venue_id, external_id, category_id, name) values (?, ?, ?, 'Lager') returning id",
                venue,
                ext,
                category,
            )
        val section = id("insert into pos.section (venue_id, external_id, name) values (?, ?, 'Hall') returning id", venue, ext)
        val desk =
            id("insert into pos.desk (venue_id, external_id, section_id, name) values (?, ?, ?, 'T1') returning id", venue, ext, section)
        val staff = id("insert into pos.staff_member (connection_id, external_id) values (?, ?) returning id", connection, ext)
        val bill =
            id(
                """
                insert into pos.bill (venue_id, external_id, paid_at, business_day, total_minor, currency, desk_id,
                                      created_by_staff_id, external_modified_at)
                values (?, ?, now(), current_date, 100, 'CZK', ?, ?, now()) returning id
                """.trimIndent(),
                venue,
                ext,
                desk,
                staff,
            )
        val billItem =
            id(
                """
                insert into pos.bill_item (bill_id, venue_id, line_no, product_id, name, quantity, total_minor)
                values (?, ?, 1, ?, 'Lager', 1, 100) returning id
                """.trimIndent(),
                bill,
                venue,
                product,
            )
        jdbc.update("insert into pos.bill_item_discount (bill_item_id) values (?)", billItem)
        jdbc.update("insert into pos.payment (bill_id, method_raw, method_kind, amount_minor) values (?, 'card', 'CARD', 100)", bill)
        jdbc.update("insert into pos.raw_payload (venue_id, resource, external_id, payload) values (?, 'bills', ?, '{}')", venue, ext)

        jdbc.update("insert into reporting.hourly_sales (venue_id, business_day, hour) values (?, current_date, 12)", venue)
        jdbc.update("insert into reporting.hourly_activity (venue_id, business_day, hour) values (?, current_date, 12)", venue)
        jdbc.update(
            "insert into reporting.product_hourly_sales (venue_id, business_day, hour, product_id) values (?, current_date, 12, ?)",
            venue,
            product,
        )
        jdbc.update("insert into reporting.staff_daily_sales (venue_id, business_day, staff_id) values (?, current_date, ?)", venue, staff)
        jdbc.update("insert into reporting.desk_daily_sales (venue_id, business_day, desk_id) values (?, current_date, ?)", venue, desk)
        jdbc.update(
            "insert into reporting.discount_daily (venue_id, business_day, discount_type) values (?, current_date, 'HAPPY_HOUR')",
            venue,
        )
        jdbc.update("insert into reporting.desk_capacity (venue_id, desk_id) values (?, ?)", venue, desk)
        jdbc.update("insert into reporting.rollup_state (venue_id, business_day, refreshed_at) values (?, current_date, now())", venue)

        return Seeded(org, venue, user, connection, bill, billItem)
    }

    private companion object {
        /** Personal data of a user, deleted with the user by `DELETE /me`, not with an organization. */
        val USER_SCOPED = setOf("account.app_user", "account.user_identity", "account.email_login_code", "account.refresh_token")

        private val byOrganization = Scope("organization_id") { it.organizationId }
        private val byVenue = Scope("venue_id") { it.venueId }
        private val byConnection = Scope("connection_id") { it.connectionId }

        val SCOPES =
            mapOf(
                "account.organization" to Scope("id") { it.organizationId },
                "account.venue" to byOrganization,
                "account.membership" to byOrganization,
                "account.followed_venue" to byVenue,
                "pos.pos_connection" to byOrganization,
                "pos.venue_link" to byVenue,
                "pos.sync_cursor" to byVenue,
                "pos.sync_job" to byConnection,
                "pos.category" to byVenue,
                "pos.product" to byVenue,
                "pos.section" to byVenue,
                "pos.desk" to byVenue,
                "pos.staff_member" to byConnection,
                "pos.bill" to byVenue,
                "pos.bill_item" to byVenue,
                "pos.bill_item_discount" to Scope("bill_item_id") { it.billItemId },
                "pos.payment" to Scope("bill_id") { it.billId },
                "pos.raw_payload" to byVenue,
                "reporting.hourly_sales" to byVenue,
                "reporting.hourly_activity" to byVenue,
                "reporting.product_hourly_sales" to byVenue,
                "reporting.staff_daily_sales" to byVenue,
                "reporting.desk_daily_sales" to byVenue,
                "reporting.discount_daily" to byVenue,
                "reporting.desk_capacity" to byVenue,
                "reporting.rollup_state" to byVenue,
            )
    }
}
