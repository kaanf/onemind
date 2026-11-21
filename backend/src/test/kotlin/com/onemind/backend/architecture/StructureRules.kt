package com.onemind.backend.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * ADR 0013 §7 as ArchUnit rules (the jOOQ schema rules R1/R3 are in [DatabaseAccessRules], the module
 * graph R8 is Spring Modulith's). Roots are parameters so every rule is proven against fixtures in
 * `architecture.fixture` (`StructureRulesTest`).
 */
object StructureRules {
    val BUSINESS_MODULES = listOf("account", "pos", "reporting", "stock")

    /** R2: collaborators that don't count against the limit. */
    val R2_ALLOWLIST = setOf("java.time.Clock", "org.springframework.transaction.support.TransactionTemplate")
    const val R2_MAX_COLLABORATORS = 5

    private val TRANSACTION_TYPES =
        setOf(TransactionTemplate::class.java.name, "org.springframework.transaction.support.TransactionOperations")

    /** R9: the only interfaces allowed besides sealed result types (ADR 0013, owner decision). */
    val R9_ALLOWLIST = setOf("PosProvider", "ReceiptExtractor", "LlmClient")

    fun all(
        appRoot: String = "com.onemind.backend",
        dbRoot: String = "com.onemind.db",
        apiRoot: String = "com.onemind.api.v1",
    ): List<ArchRule> =
        direction(appRoot) +
            listOf(
                collaborators(appRoot),
                noJooqResultsOutsidePersistence(appRoot),
                openApiOnlyInWeb(appRoot, apiRoot),
                pureDomain(appRoot, dbRoot),
            ) + transactionsOnlyInApplication(appRoot) +
            interfacesOnlyWithSecondImplementation(appRoot)

    /** §2 direction, one layered architecture per business module; cross-module edges are R8. */
    fun direction(appRoot: String): List<ArchRule> =
        BUSINESS_MODULES.map { module ->
            val m = "$appRoot.$module"
            layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("api")
                // Exact package: api.events is its own layer.
                .definedBy(m + ".api")
                .layer("events")
                .definedBy("$m.api.events..")
                .layer("web")
                .definedBy("$m.web..")
                .layer("application")
                .definedBy("$m.application..")
                .layer("domain")
                .definedBy("$m.domain..")
                .layer("persistence")
                .definedBy("$m.infra.persistence..")
                .layer("client")
                .definedBy("$m.infra.client..")
                .whereLayer("web")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("api")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("application")
                .mayOnlyBeAccessedByLayers("web", "api")
                .whereLayer("events")
                .mayOnlyBeAccessedByLayers("application")
                .whereLayer("persistence")
                .mayOnlyBeAccessedByLayers("application")
                .whereLayer("client")
                .mayOnlyBeAccessedByLayers("application")
                .whereLayer("domain")
                .mayOnlyBeAccessedByLayers("web", "api", "application", "persistence", "client")
                .because("module $module follows the package direction of ADR 0013 §2")
        }

    /** R2: application beans are use cases with at most 5 collaborators, not `*Service` classes. */
    fun collaborators(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..application..")
            .and(springBeans)
            .should(beSmallUseCases())
            .because("use cases stay small (ADR 0013, R2)")
            .allowEmptyShould(true)

    /** R4: no public signature in persistence exposes a jOOQ `Record` or `Result`. */
    fun noJooqResultsOutsidePersistence(appRoot: String): ArchRule =
        methods()
            .that()
            .arePublic()
            .and()
            // Kotlin's synthetic accessors (`access$…`) for private helpers aren't API.
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and()
            .areDeclaredInClassesThat()
            .resideInAPackage("$appRoot..infra.persistence..")
            .should(notExposeJooqResults())
            .because("jOOQ records never leave infra.persistence (ADR 0013, R4)")
            .allowEmptyShould(true)

    /** R5: generated OpenAPI types only in `web` packages (a module's or `common.web`). */
    fun openApiOnlyInWeb(
        appRoot: String,
        apiRoot: String,
    ): ArchRule =
        noClasses()
            .that()
            .resideInAPackage("$appRoot..")
            .and()
            .resideOutsideOfPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("$apiRoot..")
            .because("generated OpenAPI models never leave web (ADR 0013, R5)")
            .allowEmptyShould(true)

    /** R6: domain is pure Kotlin. */
    fun pureDomain(
        appRoot: String,
        dbRoot: String,
    ): ArchRule =
        noClasses()
            .that()
            .resideInAPackage("$appRoot..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "org.jooq..", "$dbRoot..", "tools.jackson..", "com.fasterxml.jackson..")
            .because("domain imports no Spring, jOOQ or Jackson (ADR 0013, R6)")
            .allowEmptyShould(true)

    /** R7: transactions only in `application` of business modules; in `common` never in infra or web. */
    fun transactionsOnlyInApplication(appRoot: String): List<ArchRule> =
        listOf(
            classes()
                .that()
                .resideInAnyPackage(*BUSINESS_MODULES.map { "$appRoot.$it.." }.toTypedArray())
                .and()
                .resideOutsideOfPackage("..application..")
                .should(notUseTransactions())
                .because("transactions are opened in application (ADR 0013, R7)")
                .allowEmptyShould(true),
            classes()
                .that()
                .resideInAnyPackage("$appRoot.common..infra..", "$appRoot.common..web..")
                .should(notUseTransactions())
                .because("common opens transactions at an area's root, not in infra or web (ADR 0013, R7)")
                .allowEmptyShould(true),
        )

    /** R9: interfaces only when sealed (result types) or on the allowlist. */
    fun interfacesOnlyWithSecondImplementation(appRoot: String): ArchRule =
        classes()
            .that()
            .resideInAPackage("$appRoot..")
            .and()
            .areInterfaces()
            .and()
            .areNotAnnotations()
            .and()
            // package-info.java compiles to a synthetic interface.
            .doNotHaveSimpleName("package-info")
            .should(beSealedOrAllowlisted())
            .because("no interface without a second implementation (ADR 0013, R9)")
            .allowEmptyShould(true)

    private val springBeans =
        DescribedPredicate.describe<JavaClass>("are Spring beans (@Service or @Component)") {
            it.isAnnotatedWith(Service::class.java) || it.isAnnotatedWith(Component::class.java)
        }

    private fun beSmallUseCases() =
        object : ArchCondition<JavaClass>("have at most $R2_MAX_COLLABORATORS collaborators and no name ending in Service") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                if (item.simpleName.endsWith("Service")) {
                    events.add(SimpleConditionEvent.violated(item, "${item.name} is named *Service; name the use case"))
                }
                item.constructors
                    .filter { JavaModifier.SYNTHETIC !in it.modifiers }
                    .forEach { constructor ->
                        val counted = constructor.rawParameterTypes.count { it.name !in R2_ALLOWLIST }
                        if (counted > R2_MAX_COLLABORATORS) {
                            events.add(
                                SimpleConditionEvent.violated(item, "${item.name} has $counted collaborators (max $R2_MAX_COLLABORATORS)"),
                            )
                        }
                    }
            }
        }

    private fun notExposeJooqResults() =
        object : ArchCondition<JavaMethod>("not take or return jOOQ Record or Result types") {
            override fun check(
                item: JavaMethod,
                events: ConditionEvents,
            ) {
                val exposed = (item.rawParameterTypes + item.rawReturnType).filter { it.isJooqResult() }
                if (exposed.isNotEmpty()) {
                    events.add(SimpleConditionEvent.violated(item, "${item.fullName} exposes ${exposed.joinToString { it.name }}"))
                }
            }
        }

    private fun JavaClass.isJooqResult() = isAssignableTo("org.jooq.Record") || isAssignableTo("org.jooq.Result")

    // A positive condition under classes(): with noClasses() ArchUnit would invert its events.
    private fun notUseTransactions() =
        object : ArchCondition<JavaClass>("not use TransactionTemplate or @Transactional") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                val template = item.directDependenciesFromSelf.any { it.targetClass.name in TRANSACTION_TYPES }
                val annotated =
                    item.isAnnotatedWith(Transactional::class.java) ||
                        item.methods.any { it.isAnnotatedWith(Transactional::class.java) } ||
                        item.isAnnotatedWith("jakarta.transaction.Transactional") ||
                        item.methods.any { it.isAnnotatedWith("jakarta.transaction.Transactional") }
                if (template || annotated) {
                    events.add(SimpleConditionEvent.violated(item, "${item.name} uses transactions outside application"))
                }
            }
        }

    private fun beSealedOrAllowlisted() =
        object : ArchCondition<JavaClass>("be sealed or one of $R9_ALLOWLIST") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                if (item.simpleName in R9_ALLOWLIST || item.reflect().kotlin.isSealed) return
                events.add(SimpleConditionEvent.violated(item, "${item.name} is an interface without an ADR 0013 exception"))
            }
        }
}
