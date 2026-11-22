package com.onemind.archfixture.reporting.infra.persistence

import org.jooq.DatePart
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// Rule 6 in SQL: each class lets the database decide a date, an hour or a time zone through jOOQ.
private val paidAt: Field<Instant> = DSL.field(DSL.name("paid_at"), SQLDataType.INSTANT)

class TruncatesToDay {
    fun day() = DSL.trunc(paidAt, DatePart.DAY)
}

class ExtractsHour {
    fun hour() = DSL.hour(paidAt)
}

class ReadsTimeZone {
    fun offsetHour() = DSL.timezoneHour(paidAt)
}

class CastsToDate {
    fun day() = paidAt.cast(SQLDataType.LOCALDATE)
}

class UsesDatabaseDate {
    fun today() = DSL.currentLocalDate()
}

class FormatsDate {
    fun hourText() = DSL.toChar(paidAt, DSL.inline("HH24"))
}

class CastsByClass {
    fun day() = paidAt.cast(LocalDate::class.java)
}

class DslCastsByClass {
    fun local() = DSL.cast(paidAt, LocalDateTime::class.java)
}

class CoercesByClass {
    fun day() = paidAt.coerce(LocalDate::class.java)
}

// Allowed: a cast that isn't to a date or time type.
class CastsToText {
    fun text() = paidAt.cast(String::class.java)
}

// Allowed: instants are compared and stored as they are; the day and hour come from Kotlin.
class ComparesInstants {
    fun paidBefore(at: Instant) = paidAt.lt(at)
}
