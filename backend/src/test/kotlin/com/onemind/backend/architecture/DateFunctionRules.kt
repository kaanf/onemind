package com.onemind.backend.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import java.io.File

/**
 * Rule 6 for SQL (`docs/domain/METRICS.md`, business day): the database never decides a business day or
 * an hour bucket. Business dates and local hours are computed by `common/time/BusinessDayCalendar` in
 * Kotlin and stored as columns. Two gates:
 * - [sqlText]: no date or time-zone function in migrations or in SQL text inside Kotlin.
 * - [noJooqDateFunctions]: no jOOQ call that does the same, including casts to date/time types by
 *   `SQLDataType` or by a `java.time` class literal in the same method. Not seen: a class that reaches
 *   the cast through a variable or a parameter (no literal in the casting method); the reviewer checks.
 */
object DateFunctionRules {
    /** SQL that turns an instant into a date, an hour or local time; matched without case. */
    private val SQL_PATTERNS =
        listOf(
            "date_trunc" to Regex("""\bdate_trunc\s*\(""", RegexOption.IGNORE_CASE),
            "date_part" to Regex("""\bdate_part\s*\(""", RegexOption.IGNORE_CASE),
            "at time zone" to Regex("""\bat\s+time\s+zone\b""", RegexOption.IGNORE_CASE),
            "::date" to Regex("""::\s*date\b""", RegexOption.IGNORE_CASE),
            "date(…)" to Regex("""(?<![\w.])date\s*\(""", RegexOption.IGNORE_CASE),
            "extract(…)" to Regex("""\bextract\s*\(""", RegexOption.IGNORE_CASE),
            "current_date" to Regex("""\bcurrent_date\b""", RegexOption.IGNORE_CASE),
            "cast(… as date)" to Regex("""\bas\s+date\b""", RegexOption.IGNORE_CASE),
            "timezone(…)" to Regex("""\btimezone\s*\(""", RegexOption.IGNORE_CASE),
            "to_char(…)" to Regex("""\bto_char\s*\(""", RegexOption.IGNORE_CASE),
            "date_bin(…)" to Regex("""\bdate_bin\s*\(""", RegexOption.IGNORE_CASE),
            "::timestamp / ::time" to Regex("""::\s*(timestamp|time)\b""", RegexOption.IGNORE_CASE),
            "cast(… as timestamp / time)" to Regex("""\bas\s+(timestamp|time)\b""", RegexOption.IGNORE_CASE),
        )

    /** The forbidden functions found in [sql], comments ignored; empty when it is clean. */
    fun sqlText(sql: String): List<String> {
        val code =
            sql
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                .replace(Regex("""--[^\n]*"""), " ")
        return SQL_PATTERNS.filter { (_, pattern) -> pattern.containsMatchIn(code) }.map { it.first }
    }

    /**
     * The forbidden functions in string literals of Kotlin [source]. Strings and comments are matched in one
     * pass, so `//` inside a string (a URL) isn't taken for a comment, and SQL in comments is ignored.
     */
    fun kotlinText(source: String): List<String> {
        val tokens = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\\\n]|\\\\.)*\"|//[^\\n]*|/\\*[\\s\\S]*?\\*/")
        val strings = tokens.findAll(source).map { it.value }.filter { it.startsWith("\"") }
        return sqlText(strings.joinToString("\n")).distinct()
    }

    /** Every file under [dir] with [extension] and the forbidden functions it holds, only those with any. */
    fun scan(
        dir: File,
        extension: String,
        find: (String) -> List<String>,
    ): Map<String, List<String>> =
        files(dir, extension)
            .associate { it.relativeTo(dir).path to find(it.readText()) }
            .filterValues { it.isNotEmpty() }

    fun files(
        dir: File,
        extension: String,
    ): List<File> = dir.walkTopDown().filter { it.isFile && it.extension == extension }.toList()

    // jOOQ: date parts, truncation, extraction, time zones and casts to date/time types (jOOQ has no
    // time-zone conversion; `… at time zone` would be plain SQL text, caught by [kotlinText]).
    private val DSL_METHODS =
        setOf(
            "trunc",
            "date",
            "extract",
            "datePart",
            "century",
            "decade",
            "year",
            "quarter",
            "month",
            "week",
            "day",
            "dayOfWeek",
            "isoDayOfWeek",
            "dayOfYear",
            "hour",
            "minute",
            "epoch",
            "atTimeZone",
            "currentDate",
            "currentLocalDate",
            "toDate",
            "toLocalDate",
            "timezone",
            "timezoneHour",
            "timezoneMinute",
            "toChar",
            "toTimestamp",
        )
    private val FIELD_METHODS = setOf("atTimeZone")
    private val CAST_METHODS = setOf("cast", "coerce")
    private val JAVA_TIME_CLASSES =
        setOf(
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.LocalDateTime",
            "java.time.OffsetDateTime",
            "java.time.ZonedDateTime",
        )
    private val DATE_TYPES = setOf("DATE", "LOCALDATE", "TIME", "LOCALTIME", "TIMESTAMP", "LOCALDATETIME", "TIMESTAMPWITHTIMEZONE")

    /** No jOOQ call or type that makes the database decide a date, an hour or a time zone. */
    fun noJooqDateFunctions(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..")
            .should(notUseJooqDateFunctions())
            .because("business days and local hours come from common/time/BusinessDayCalendar, never from SQL (AGENTS.md rule 6)")

    private fun notUseJooqDateFunctions() =
        object : ArchCondition<JavaClass>("not use jOOQ date, hour, time-zone or date-cast functions") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.accessesFromSelf
                    .filter { forbidden.test(it) }
                    .forEach { events.add(SimpleConditionEvent.violated(item, it.description)) }
                item.directDependenciesFromSelf
                    .filter { it.targetClass.name == "org.jooq.DatePart" }
                    .forEach { events.add(SimpleConditionEvent.violated(item, it.description)) }
                // A cast by class (`cast(LocalDate::class.java)`, `coerce(…)`): ArchUnit can't see call arguments,
                // but it sees the class literal; a cast or coerce next to a java.time literal in one method is caught.
                item.codeUnits
                    .filter { unit -> unit.methodCallsFromSelf.any { isJooqCast(it) } }
                    .filter { unit -> unit.referencedClassObjects.any { it.rawType.name in JAVA_TIME_CLASSES } }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.fullName} casts a jOOQ field to a java.time class")) }
            }
        }

    private fun isJooqCast(call: JavaAccess<*>): Boolean =
        call.target.name in CAST_METHODS &&
            (call.targetOwner.name == "org.jooq.impl.DSL" || call.targetOwner.isAssignableTo("org.jooq.Field"))

    private val forbidden =
        DescribedPredicate.describe<JavaAccess<*>>("a jOOQ date function") { access ->
            val owner = access.targetOwner.name
            val name = access.target.name
            when {
                owner == "org.jooq.impl.DSL" -> name in DSL_METHODS

                owner == "org.jooq.impl.SQLDataType" -> name in DATE_TYPES

                // On Field and every subtype (TableField, generated columns).
                access.targetOwner.isAssignableTo("org.jooq.Field") -> name in FIELD_METHODS

                else -> false
            }
        }
}
