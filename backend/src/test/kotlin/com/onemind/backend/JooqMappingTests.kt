package com.onemind.backend

import com.onemind.db.account.tables.references.APP_USER
import com.onemind.db.account.tables.references.EMAIL_LOGIN_CODE
import com.onemind.db.account.tables.references.ORGANIZATION
import com.onemind.db.account.tables.references.VENUE
import org.jooq.DSLContext
import org.jooq.postgres.extensions.types.Inet
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Type mappings configured for jOOQ codegen in build.gradle.kts.
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@Transactional
class JooqMappingTests {
    @Autowired
    lateinit var dsl: DSLContext

    @Test
    fun `citext email comparison ignores case`() {
        dsl.insertInto(APP_USER).set(APP_USER.EMAIL, "Owner@Example.com").execute()

        assertEquals(1, dsl.fetchCount(APP_USER, APP_USER.EMAIL.eq("owner@example.com")))
    }

    @Test
    fun `currency and timestamptz map to Currency and Instant`() {
        val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS)
        val organizationId =
            dsl
                .insertInto(ORGANIZATION)
                .set(ORGANIZATION.NAME, "Bar group")
                .returning(ORGANIZATION.ID)
                .fetchSingle()
                .id
        val venue =
            dsl
                .insertInto(VENUE)
                .set(VENUE.ORGANIZATION_ID, organizationId)
                .set(VENUE.NAME, "Bar")
                .set(VENUE.CURRENCY, Currency.getInstance("CZK"))
                .returning()
                .fetchSingle()
        val code =
            dsl
                .insertInto(EMAIL_LOGIN_CODE)
                .set(EMAIL_LOGIN_CODE.EMAIL, "owner@example.com")
                .set(EMAIL_LOGIN_CODE.CODE_HASH, "hash")
                .set(EMAIL_LOGIN_CODE.EXPIRES_AT, expiresAt)
                .set(EMAIL_LOGIN_CODE.REQUESTED_IP, Inet.inet(InetAddress.getByName("192.0.2.10")))
                .returning()
                .fetchSingle()

        assertEquals(Currency.getInstance("CZK"), venue.currency)
        assertNotNull(venue.createdAt)
        assertEquals(expiresAt, code.expiresAt)
        assertEquals(InetAddress.getByName("192.0.2.10"), code.requestedIp?.address())
    }
}
