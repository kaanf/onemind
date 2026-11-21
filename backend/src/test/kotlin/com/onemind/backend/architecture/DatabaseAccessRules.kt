package com.onemind.backend.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * jOOQ package rules from ADR 0013 (R1): each module uses only its own schema package (`common`
 * owns `platform`); data of another module comes through its `api` or events, never its tables.
 * Only `infra.persistence` packages touch jOOQ (`org.jooq..` and the generated classes; R3), also in
 * `common`.
 * Roots are parameters so the rules can be tested against fixtures.
 */
object DatabaseAccessRules {
    // Module → schemas whose jOOQ package it may use.
    private val allowedSchemas =
        mapOf(
            "account" to setOf("account"),
            "pos" to setOf("pos"),
            "reporting" to setOf("reporting"),
            "stock" to setOf("stock"),
            "common" to setOf("platform"),
        )
    private val schemas = allowedSchemas.keys

    fun rules(
        appRoot: String = "com.onemind.backend",
        dbRoot: String = "com.onemind.db",
    ): List<ArchRule> =
        allowedSchemas.map { (module, allowed) ->
            noClasses()
                .that()
                .resideInAPackage("$appRoot.$module..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*(schemas - allowed).map { "$dbRoot.$it.." }.toTypedArray())
                .because("module $module owns only its schema (ADR 0013, R1)")
                .allowEmptyShould(true)
        } +
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .and()
                .resideOutsideOfPackages(*StructureRules.persistencePackages(appRoot).toTypedArray())
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("$dbRoot..", "org.jooq..")
                .because("jOOQ types stay inside infra.persistence (ADR 0013, R3)")
                .allowEmptyShould(true)
}
