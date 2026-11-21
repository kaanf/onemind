package com.onemind.backend.architecture

import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaFieldAccess
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import java.lang.reflect.Executable

/**
 * AGENTS.md product rules 1, 5 and 6 as ArchUnit rules. Roots are parameters so every rule is proven
 * against fixtures in `com.onemind.archfixture` (`ProductRulesTest`).
 */
object ProductRules {
    /**
     * Rule 5: whole camelCase/snake_case words (singular; a plural `s`/`es` is stripped) that make a
     * class, member or parameter money-related. `docs/api/redocly.yaml` holds the same list
     * (`ProductRulesTest` keeps them equal).
     */
    val MONEY_WORDS =
        setOf(
            "money",
            "amount",
            "price",
            "total",
            "subtotal",
            "revenue",
            "sales",
            "cost",
            "tip",
            "discount",
            "loss",
            "payment",
            "refund",
            "bill",
            "net",
            "gross",
            "minor",
        )

    /** A name whose last word is one of these is a ratio or a count, not money (`discountRate`, `totalCount`). */
    val NOT_MONEY_SUFFIXES = setOf("rate", "ratio", "percent", "pct", "share", "count", "quantity")

    private val FLOATING_TYPES = setOf("float", "double", "java.lang.Float", "java.lang.Double")

    /** Rule 6: the only class that may read the system clock. */
    const val CLOCK_SOURCE = "common.time.ClockConfig"

    /** Rule 6: the only package that may turn an instant into a local date or time. */
    const val TIME_PACKAGE = "common.time"

    private val DATE_TIME_TYPES =
        setOf(
            "Instant",
            "LocalDate",
            "LocalDateTime",
            "LocalTime",
            "ZonedDateTime",
            "OffsetDateTime",
            "OffsetTime",
            "Year",
            "YearMonth",
            "MonthDay",
        ).map { "java.time.$it" }.toSet()

    /** Rule 1: HTTP client methods that can send something other than GET, by declaring type (or a subtype). */
    private val MUTATING_HTTP_CALLS: Map<String, (String) -> Boolean> =
        mapOf(
            "org.springframework.web.client.RestClient" to { it in setOf("post", "put", "patch", "delete", "method") },
            // exchange/execute take the method as an argument, so they are out entirely.
            "org.springframework.web.client.RestOperations" to ::isMutatingRestTemplateCall,
            "org.springframework.web.reactive.function.client.WebClient" to { it in setOf("post", "put", "patch", "delete", "method") },
            "org.springframework.http.RequestEntity" to { it in setOf("post", "put", "patch", "delete", "method") },
            // The method name is a runtime value, so building one from a string is out too.
            "org.springframework.http.HttpMethod" to { it in setOf("valueOf", "resolve") },
            "java.net.http.HttpRequest\$Builder" to { it in setOf("POST", "PUT", "DELETE", "method") },
            "java.net.HttpURLConnection" to { it == "setRequestMethod" },
        )

    private val MUTATING_HTTP_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

    private val MUTATING_EXCHANGE_ANNOTATIONS =
        setOf("Post", "Put", "Patch", "Delete").map { "org.springframework.web.service.annotation.${it}Exchange" }.toSet()

    fun all(appRoot: String = "com.onemind.backend"): List<ArchRule> =
        listOf(noFloatingPointMoney(appRoot), noSystemTime(appRoot), localDatesOnlyInCommonTime(appRoot), posIsReadOnly(appRoot))

    /**
     * Rule 5: no `Float`/`Double` in a money-related field, method, constructor or parameter, and no
     * `BigDecimal` built from one. Parameter names need `-java-parameters` (backend/build.gradle.kts).
     */
    fun noFloatingPointMoney(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..")
            .should(notHoldMoneyAsFloatingPoint())
            .because("money is integer minor units or BigDecimal, never Float/Double (AGENTS.md rule 5)")
            .allowEmptyShould(true)

    /** Rule 6: "now" comes from the injected `Clock`; only [CLOCK_SOURCE] reads the system clock or zone. */
    fun noSystemTime(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..")
            .and()
            .doNotHaveFullyQualifiedName("$appRoot.$CLOCK_SOURCE")
            .should(notReadSystemTime())
            .because("inject Clock and use common/time/BusinessDayCalendar (AGENTS.md rule 6)")
            .allowEmptyShould(true)

    /** Rule 6: an instant becomes a local date or time only in [TIME_PACKAGE], so business days have one entry point. */
    fun localDatesOnlyInCommonTime(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..")
            .and()
            .resideOutsideOfPackage("$appRoot.$TIME_PACKAGE..")
            .should(notConvertInstantsToLocalTime())
            .because("business days come from common/time/BusinessDayCalendar, never a calendar date (AGENTS.md rule 6)")
            .allowEmptyShould(true)

    /** Rule 1: nothing in the `pos` module can send a mutating HTTP request. */
    fun posIsReadOnly(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot.pos..")
            .should(notSendMutatingHttp())
            .because("the POS is read-only: we never write to Storyous (AGENTS.md rule 1)")
            .allowEmptyShould(true)

    /** Splits `getTotalPrice`, `amount_minor` or `Payment$Companion` into lower-case words; drops a mangled `-<hash>`. */
    fun tokens(name: String): List<String> =
        name
            .substringBefore('-')
            .split('_', '$')
            .flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    fun isMoneyName(name: String): Boolean {
        val words = tokens(name)
        return words.isNotEmpty() && words.last() !in NOT_MONEY_SUFFIXES && words.any { it.isMoneyWord() }
    }

    private fun String.isMoneyWord() =
        this in MONEY_WORDS ||
            (endsWith("s") && dropLast(1) in MONEY_WORDS) ||
            (endsWith("es") && dropLast(2) in MONEY_WORDS)

    private fun isNotMoneyName(name: String) = tokens(name).lastOrNull() in NOT_MONEY_SUFFIXES

    private fun isMutatingRestTemplateCall(name: String) =
        name.startsWith("post") || name.startsWith("patch") || name in setOf("put", "delete", "exchange", "execute")

    private fun notHoldMoneyAsFloatingPoint() =
        object : ArchCondition<JavaClass>("not hold money as Float/Double") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val moneyClass = isMoneyName(item.name.substringAfterLast('.'))

                fun inMoneyContext(name: String) = !isNotMoneyName(name) && (moneyClass || isMoneyName(name))

                fun violated(message: String) = events.add(SimpleConditionEvent.violated(item, "$message Float/Double money"))

                item.fields
                    .filter { inMoneyContext(it.name) && it.type.isFloating() }
                    .forEach { violated("${it.fullName} holds") }
                item.methods
                    .filter { it.isOwnCode() && inMoneyContext(it.name) }
                    .filter { m -> m.returnType.isFloating() || m.parameterTypes.any { it.isFloating() } }
                    .forEach { violated("${it.fullName} takes or returns") }
                item.codeUnits
                    .filter { it.isOwnCode() }
                    .forEach { unit ->
                        unit.namedParameters().filter { (name, floating) -> floating && inMoneyContext(name) }.forEach { (name) ->
                            violated("${unit.fullName} parameter $name is")
                        }
                    }
                item.codeUnitCallsFromSelf
                    .filter {
                        it.targetOwner.name == "java.math.BigDecimal" &&
                            it.target.rawParameterTypes.any { p -> p.name in FLOATING_TYPES }
                    }.forEach { violated("${it.description}: BigDecimal from a") }
                // Kotlin's inline Double.toBigDecimal() compiles to BigDecimal(String.valueOf(double)).
                val stringFromFloating =
                    item.codeUnitCallsFromSelf.any {
                        it.targetOwner.name == "java.lang.String" &&
                            it.target.name == "valueOf" &&
                            it.target.rawParameterTypes.any { p -> p.name in FLOATING_TYPES }
                    }
                val bigDecimalFromString =
                    item.codeUnitCallsFromSelf.any {
                        it.targetOwner.name == "java.math.BigDecimal" &&
                            it.target.name == "<init>" &&
                            it.target.rawParameterTypes.map { p -> p.name } == listOf("java.lang.String")
                    }
                if (stringFromFloating && bigDecimalFromString) violated("${item.name} builds a BigDecimal from a")
            }
        }

    private fun JavaType.isFloating() = allInvolvedRawTypes.any { it.name in FLOATING_TYPES }

    // Kotlin's generated componentN/copy (mangled `copy-<hash>` with a value-class property) and other
    // synthetic members repeat what the fields already show.
    private fun JavaCodeUnit.isOwnCode() =
        JavaModifier.SYNTHETIC !in modifiers && !name.matches(Regex("component\\d+|copy(-.+)?(\\\$default)?"))

    /** Parameter names (from `-java-parameters`) and whether each is a `Float`/`Double`. */
    private fun JavaCodeUnit.namedParameters(): List<Pair<String, Boolean>> {
        val executable = runCatching { reflect() as? Executable }.getOrNull() ?: return emptyList()
        return executable.parameters
            .filter { it.isNamePresent && !it.isSynthetic && !it.isImplicit }
            .map { p ->
                val floating =
                    p.type.name in FLOATING_TYPES ||
                        Regex("\\bjava\\.lang\\.(Float|Double)\\b").containsMatchIn(p.parameterizedType.typeName)
                p.name to floating
            }
    }

    private fun notReadSystemTime() =
        object : ArchCondition<JavaClass>("not read the system clock or time zone") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.codeUnitCallsFromSelf
                    .filter { it.readsSystemTime() }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: inject Clock instead")) }
                item.fieldAccessesFromSelf
                    .filter { it.targetOwner.name in KOTLIN_SYSTEM_CLOCKS }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: inject Clock instead")) }
            }
        }

    private val KOTLIN_SYSTEM_CLOCKS = setOf("kotlin.time.Clock\$System", "kotlinx.datetime.Clock\$System")

    private fun JavaCall<*>.readsSystemTime(): Boolean {
        val owner = targetOwner.name
        val name = target.name
        val params = target.rawParameterTypes.map { it.name }
        return when {
            // now(Clock) is the injected-clock path; now() and now(ZoneId) read the system clock.
            owner in DATE_TIME_TYPES -> name == "now" && "java.time.Clock" !in params

            // tick(Clock, Duration) wraps an injected clock; tickSeconds(ZoneId) and friends read the system one.
            owner == "java.time.Clock" -> name.startsWith("system") || (name.startsWith("tick") && "java.time.ZoneId" in params)

            owner == "java.time.ZoneId" -> name == "systemDefault"

            owner == "java.util.TimeZone" -> name == "getDefault"

            owner == "java.lang.System" -> name == "currentTimeMillis"

            owner == "java.util.Date" || owner == "java.util.GregorianCalendar" -> name == "<init>" && params.isEmpty()

            owner == "java.util.Calendar" -> name == "getInstance"

            owner in KOTLIN_SYSTEM_CLOCKS -> name == "now"

            else -> false
        }
    }

    private fun notConvertInstantsToLocalTime() =
        object : ArchCondition<JavaClass>("not turn an instant into a local date or time") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.codeUnitCallsFromSelf
                    .filter { it.convertsInstantToLocal() }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: use common/time/BusinessDayCalendar")) }
            }
        }

    private val LOCAL_PARTS =
        setOf(
            "toLocalDate",
            "toLocalDateTime",
            "toLocalTime",
            "getHour",
            "getDayOfWeek",
            "getDayOfMonth",
            "getDayOfYear",
            "getMonth",
            "getMonthValue",
            "getYear",
        )

    private fun JavaCall<*>.convertsInstantToLocal(): Boolean {
        val owner = targetOwner.name
        val name = target.name
        return when (owner) {
            "java.time.Instant" -> name == "atZone"

            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime" -> name == "ofInstant"

            // An offset date-time is fine for API mapping; its local date or hour is a business-day question.
            "java.time.ZonedDateTime", "java.time.OffsetDateTime" -> name in LOCAL_PARTS

            else -> false
        }
    }

    private fun notSendMutatingHttp() =
        object : ArchCondition<JavaClass>("not send POST, PUT, PATCH or DELETE requests") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.codeUnitCallsFromSelf
                    .filter { call ->
                        MUTATING_HTTP_CALLS.any { (owner, mutating) ->
                            call.targetOwner.isAssignableTo(owner) &&
                                mutating(call.target.name)
                        }
                    }.forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: mutating HTTP call")) }
                item.fieldAccessesFromSelf
                    .filter { it.isMutatingHttpMethod() }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: mutating HTTP method")) }
                // A type-level @HttpExchange(method = …) is inherited by method mappings that leave it empty.
                if (item.annotations.any { it.isMutatingExchange() }) {
                    events.add(SimpleConditionEvent.violated(item, "${item.name}: mutating HTTP exchange"))
                }
                item.methods
                    .filter { m -> m.annotations.any { it.isMutatingExchange() } }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.fullName}: mutating HTTP exchange")) }
            }
        }

    private fun JavaFieldAccess.isMutatingHttpMethod() =
        targetOwner.name == "org.springframework.http.HttpMethod" && target.name in MUTATING_HTTP_METHODS

    private fun JavaAnnotation<*>.isMutatingExchange() =
        rawType.name in MUTATING_EXCHANGE_ANNOTATIONS ||
            (
                rawType.name == "org.springframework.web.service.annotation.HttpExchange" &&
                    (get("method").orElse("") as String).uppercase() !in setOf("", "GET")
            )
}
