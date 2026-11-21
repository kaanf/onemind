package com.onemind.backend.architecture

import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * jOOQ package rules from ADR 0009: each module uses only its own schema package
 * (`reporting` may also read `pos`; `common` owns `platform`), and only `infra` packages touch
 * jOOQ classes.
 * Roots are parameters so the rules can be tested against fixtures.
 */
object DatabaseAccessRules {
    // Module → schemas whose jOOQ package it may use.
    private val allowedSchemas =
        mapOf(
            "account" to setOf("account"),
            "pos" to setOf("pos"),
            "reporting" to setOf("reporting", "pos"),
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
                .because("module $module owns only its schema (ADR 0009)")
                .allowEmptyShould(true)
        } +
            noClasses()
                .that()
                .resideInAPackage("$appRoot..")
                .and()
                .resideOutsideOfPackage("..infra..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("$dbRoot..")
                .because("jOOQ types stay inside infra (ADR 0002)")
                .allowEmptyShould(true)
}
