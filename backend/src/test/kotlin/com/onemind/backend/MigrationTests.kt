package com.onemind.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Rules from docs/architecture/DB_SCHEMA.md that the schema itself enforces.
// Plain SQL on purpose: these tests check the migrations, not the jOOQ layer.
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class MigrationTests {
    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Test
    fun `flyway history lives in platform and module schemas exist`() {
        val schemas =
            jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name in ('platform', 'account', 'pos', 'reporting')",
                String::class.java,
            )
        assertEquals(setOf("platform", "account", "pos", "reporting"), schemas.toSet())
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from platform.flyway_schema_history where not success"))
    }

    @Test
    fun `user email is unique ignoring case`() {
        insertUser("owner@example.com")

        assertFailsWith<DataIntegrityViolationException> { insertUser("Owner@Example.com") }
    }

    @Test
    fun `membership role is checked`() {
        val organizationId = insertOrganization()
        val userId = insertUser("staff@example.com")

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "insert into account.membership (organization_id, user_id, role) values (?, ?, 'MANAGER')",
                organizationId,
                userId,
            )
        }
    }

    @Test
    fun `bill external id is unique per venue`() {
        val venueId = insertVenue()
        insertBill(venueId, "bill-1")
        insertBill(insertVenue(), "bill-1")

        assertFailsWith<DataIntegrityViolationException> { insertBill(venueId, "bill-1") }
    }

    @Test
    fun `deleting a bill removes its items and payments`() {
        val venueId = insertVenue()
        val billId = insertBill(venueId, "bill-1")
        jdbc.update(
            "insert into pos.bill_item (bill_id, venue_id, line_no, name, quantity, total_minor) values (?, ?, 1, 'Pilsner', 2, 12000)",
            billId,
            venueId,
        )
        jdbc.update("insert into pos.payment (bill_id, method_raw, method_kind, amount_minor) values (?, 'card', 'CARD', 12000)", billId)

        jdbc.update("delete from pos.bill where id = ?", billId)

        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from pos.bill_item where bill_id = ?", billId))
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from pos.payment where bill_id = ?", billId))
    }

    @Test
    fun `deleting a user removes identities, tokens, memberships and followed venues`() {
        val userId = insertUser("leaving@example.com")
        val venueId = insertVenue()
        jdbc.update("insert into account.user_identity (user_id, provider, subject) values (?, 'APPLE', 'apple-sub')", userId)
        jdbc.update(
            "insert into account.refresh_token (user_id, family_id, token_hash, expires_at) values (?, ?, 'hash', now() + interval '30 days')",
            userId,
            UUID.randomUUID(),
        )
        jdbc.update("insert into account.membership (organization_id, user_id, role) values (?, ?, 'OWNER')", insertOrganization(), userId)
        jdbc.update("insert into account.followed_venue (user_id, venue_id) values (?, ?)", userId, venueId)

        jdbc.update("delete from account.app_user where id = ?", userId)

        for (table in listOf("user_identity", "refresh_token", "membership", "followed_venue")) {
            assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.$table where user_id = ?", userId), table)
        }
    }

    private fun insertOrganization(): UUID = insertReturningId("insert into account.organization (name) values ('Bar group') returning id")

    private fun insertUser(email: String): UUID = insertReturningId("insert into account.app_user (email) values (?) returning id", email)

    private fun insertVenue(): UUID =
        insertReturningId(
            "insert into account.venue (organization_id, name, currency) values (?, 'Bar', 'CZK') returning id",
            insertOrganization(),
        )

    private fun insertBill(
        venueId: UUID,
        externalId: String,
    ): UUID =
        insertReturningId(
            """
            insert into pos.bill (venue_id, external_id, paid_at, business_day, total_minor, currency, external_modified_at)
            values (?, ?, now(), current_date, 12000, 'CZK', now())
            returning id
            """.trimIndent(),
            venueId,
            externalId,
        )

    private fun insertReturningId(
        sql: String,
        vararg args: Any,
    ): UUID = checkNotNull(jdbc.queryForObject(sql, UUID::class.java, *args))
}
