package com.onemind.backend.architecture

import com.onemind.archfixture.reporting.infra.persistence.CastsToDate
import com.onemind.archfixture.reporting.infra.persistence.ComparesInstants
import com.onemind.archfixture.reporting.infra.persistence.ExtractsHour
import com.onemind.archfixture.reporting.infra.persistence.ReadsTimeZone
import com.onemind.archfixture.reporting.infra.persistence.TruncatesToDay
import com.onemind.archfixture.reporting.infra.persistence.UsesDatabaseDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves the rule 6 SQL gates catch each violation and let the allowed shape pass. */
class DateFunctionRulesTest {
    private val root = "com.onemind.archfixture"

    @Test
    fun `SQL that decides a date, an hour or a time zone is caught`() {
        assertEquals(listOf("date_trunc"), DateFunctionRules.sqlText("select date_trunc('day', paid_at) from pos.bill"))
        assertEquals(listOf("at time zone"), DateFunctionRules.sqlText("select paid_at AT  TIME ZONE 'Europe/Prague' from pos.bill"))
        assertEquals(listOf("::date"), DateFunctionRules.sqlText("select paid_at::date from pos.bill"))
        assertEquals(listOf("date(…)"), DateFunctionRules.sqlText("select date(paid_at) from pos.bill"))
        assertEquals(listOf("extract(…)"), DateFunctionRules.sqlText("select extract(hour from paid_at) from pos.bill"))
        assertEquals(listOf("date_part"), DateFunctionRules.sqlText("select date_part('hour', paid_at) from pos.bill"))
        assertEquals(listOf("current_date"), DateFunctionRules.sqlText("insert into x (d) values (current_date)"))
        assertEquals(listOf("cast(… as date)"), DateFunctionRules.sqlText("select cast(paid_at as date) from pos.bill"))
    }

    @Test
    fun `plain DDL, date columns, comments and similar words pass`() {
        val allowed =
            """
            -- business_day is computed in Kotlin, never with date_trunc or at time zone
            /* a bill's ::date is not decided here */
            create table reporting.x (business_day date not null, updated_at timestamptz not null default now());
            update reporting.x set updated_at = now();
            alter table pos.bill add column external_desk_id text;
            """.trimIndent()
        assertEquals(emptyList(), DateFunctionRules.sqlText(allowed))
    }

    @Test
    fun `SQL in Kotlin string literals is caught, Kotlin code and comments are not`() {
        // jOOQ has no time-zone conversion of its own; it would come as plain SQL: DSL.field("{0} at time zone …").
        val violating = "val local = DSL.field(\"{0} at time zone 'Europe/Prague'\", paidAt); val sql = \"select date_trunc('day', paid_at)\""
        val allowed =
            """
            // never date_trunc here
            fun update(date: LocalDate) = dsl.update(BILL).set(BILL.BUSINESS_DAY, date)
            val message = "The update failed"
            """.trimIndent()
        assertEquals(listOf("date_trunc", "at time zone"), DateFunctionRules.kotlinText(violating))
        assertEquals(emptyList(), DateFunctionRules.kotlinText(allowed))
    }

    @Test
    fun `jOOQ date functions are caught, comparing instants passes`() {
        val rule = DateFunctionRules.noJooqDateFunctions(root)
        assertTrue(rule.violatedBy(TruncatesToDay::class), "DSL.trunc + DatePart")
        assertTrue(rule.violatedBy(ExtractsHour::class), "DSL.hour")
        assertTrue(rule.violatedBy(ReadsTimeZone::class), "DSL.timezoneHour")
        assertTrue(rule.violatedBy(CastsToDate::class), "cast to SQLDataType.LOCALDATE")
        assertTrue(rule.violatedBy(UsesDatabaseDate::class), "DSL.currentLocalDate")
        assertFalse(rule.violatedBy(ComparesInstants::class))
    }
}
