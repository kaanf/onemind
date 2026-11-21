package com.onemind.backend.architecture

import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.core.domain.JavaCall
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaFieldAccess
import com.tngtech.archunit.core.domain.JavaMember
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.domain.JavaType
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * AGENTS.md product rules 1, 5 and 6 as ArchUnit rules. Roots are parameters so every rule is proven
 * against fixtures in `com.onemind.archfixture` (`ProductRulesTest`).
 */
object ProductRules {
    /** Rule 5: whole camelCase/snake_case tokens that make a class or member money-related. */
    val MONEY_TOKENS = setOf("money", "amount", "price", "total", "revenue", "cost", "tip", "discount", "loss", "payment", "minor")

    /** A name whose last token is one of these is a ratio or a count, not money (`discountRate`, `totalCount`). */
    val NOT_MONEY_SUFFIXES = setOf("rate", "ratio", "percent", "pct", "share", "count", "quantity")

    private val FLOATING_TYPES = setOf("float", "double", "java.lang.Float", "java.lang.Double")

    /** Rule 6: the only class that may read the system clock. */
    const val CLOCK_SOURCE = "common.time.ClockConfig"

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
        ).map { "java.time.$it" }
            .toSet()

    /** Rule 1: HTTP client methods that can send something other than GET, by declaring type. */
    private val MUTATING_HTTP_CALLS: Map<String, (String) -> Boolean> =
        mapOf(
            "org.springframework.web.client.RestClient" to { it in setOf("post", "put", "patch", "delete", "method") },
            // exchange/execute take the method as an argument, so they are out entirely.
            "org.springframework.web.client.RestTemplate" to ::isMutatingRestTemplateCall,
            "org.springframework.web.client.RestOperations" to ::isMutatingRestTemplateCall,
            "org.springframework.web.reactive.function.client.WebClient" to { it in setOf("post", "put", "patch", "delete", "method") },
            "org.springframework.http.RequestEntity" to { it in setOf("post", "put", "patch", "delete", "method") },
            "java.net.http.HttpRequest\$Builder" to { it in setOf("POST", "PUT", "DELETE", "method") },
        )

    private val MUTATING_HTTP_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")

    private val MUTATING_EXCHANGE_ANNOTATIONS =
        setOf("Post", "Put", "Patch", "Delete").map { "org.springframework.web.service.annotation.${it}Exchange" }.toSet()

    fun all(appRoot: String = "com.onemind.backend"): List<ArchRule> =
        listOf(noFloatingPointMoney(appRoot), noSystemTime(appRoot), posIsReadOnly(appRoot))

    /** Rule 5: no `Float`/`Double` in a money-related field, method signature or conversion to `BigDecimal`. */
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

    /** Rule 1: nothing in the `pos` module can send a mutating HTTP request. */
    fun posIsReadOnly(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot.pos..")
            .should(notSendMutatingHttp())
            .because("the POS is read-only: we never write to Storyous (AGENTS.md rule 1)")
            .allowEmptyShould(true)

    /** Splits `getTotalPrice`, `amount_minor` or `Payment$Companion` into lower-case words. */
    fun tokens(name: String): List<String> =
        name
            .split('_', '$', '-')
            .flatMap { it.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

    fun isMoneyName(name: String): Boolean {
        val words = tokens(name)
        return words.isNotEmpty() && words.last() !in NOT_MONEY_SUFFIXES && words.any { it in MONEY_TOKENS }
    }

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

                fun violated(
                    member: JavaMember,
                    what: String,
                ) = events.add(SimpleConditionEvent.violated(item, "${member.fullName} $what Float/Double money"))

                item.fields
                    .filter { inMoneyContext(it.name) && it.type.isFloating() }
                    .forEach { violated(it, "holds") }
                item.methods
                    // Kotlin's generated componentN/copy/accessors repeat what the fields already show.
                    .filter { JavaModifier.SYNTHETIC !in it.modifiers && !it.isDataClassMember() }
                    .filter { inMoneyContext(it.name) }
                    .filter { m -> m.returnType.isFloating() || m.parameterTypes.any { it.isFloating() } }
                    .forEach { violated(it, "takes or returns") }
                item.codeUnitCallsFromSelf
                    .filter {
                        it.targetOwner.name == "java.math.BigDecimal" &&
                            it.target.rawParameterTypes.any { p -> p.name in FLOATING_TYPES }
                    }.forEach {
                        events.add(SimpleConditionEvent.violated(item, "${it.description}: BigDecimal from a Float/Double"))
                    }
            }
        }

    private fun JavaType.isFloating() = allInvolvedRawTypes.any { it.name in FLOATING_TYPES }

    private fun JavaMember.isDataClassMember() = name.matches(Regex("component\\d+|copy|copy\\\$default"))

    private fun notReadSystemTime() =
        object : ArchCondition<JavaClass>("not read the system clock or time zone") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.codeUnitCallsFromSelf
                    .filter { it.readsSystemTime() }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: inject Clock instead")) }
            }
        }

    private fun JavaCall<*>.readsSystemTime(): Boolean {
        val owner = targetOwner.name
        val name = target.name
        val params = target.rawParameterTypes.map { it.name }
        return when {
            // now(Clock) is the injected-clock path; now() and now(ZoneId) read the system clock.
            owner in DATE_TIME_TYPES -> name == "now" && "java.time.Clock" !in params

            owner == "java.time.Clock" -> name.startsWith("system")

            owner == "java.time.ZoneId" -> name == "systemDefault"

            owner == "java.util.TimeZone" -> name == "getDefault"

            owner == "java.lang.System" -> name == "currentTimeMillis"

            owner == "java.util.Date" -> name == "<init>" && params.isEmpty()

            owner == "java.util.Calendar" -> name == "getInstance"

            owner == "kotlin.time.Clock\$System" || owner == "kotlinx.datetime.Clock\$System" -> name == "now"

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
                    .filter { MUTATING_HTTP_CALLS[it.targetOwner.name]?.invoke(it.target.name) == true }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: mutating HTTP call")) }
                item.fieldAccessesFromSelf
                    .filter { it.isMutatingHttpMethod() }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.description}: mutating HTTP method")) }
                item.methods
                    .filter { m -> m.annotations.any { it.rawType.name in MUTATING_EXCHANGE_ANNOTATIONS || it.isMutatingHttpExchange() } }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${it.fullName}: mutating HTTP exchange")) }
            }
        }

    private fun JavaFieldAccess.isMutatingHttpMethod() =
        targetOwner.name == "org.springframework.http.HttpMethod" && target.name in MUTATING_HTTP_METHODS

    private fun JavaAnnotation<*>.isMutatingHttpExchange() =
        rawType.name == "org.springframework.web.service.annotation.HttpExchange" &&
            (get("method").orElse("") as String).uppercase() !in setOf("", "GET")
}
