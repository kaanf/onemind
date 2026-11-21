package com.onemind.backend.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import kotlin.reflect.KClass

/**
 * True when [types] (imported on their own) break this rule: how the `*RulesTest`s prove a gate bites.
 * A fixture import leaves most rules with nothing to check, so emptiness is allowed here; on the real
 * code it isn't (`ModuleBoundaryTests`).
 */
internal fun ArchRule.violatedBy(vararg types: KClass<*>): Boolean = listOf(this).violatedBy(*types)

internal fun List<ArchRule>.violatedBy(vararg types: KClass<*>): Boolean {
    val classes = ClassFileImporter().importClasses(*types.map { it.java }.toTypedArray())
    return any { it.allowEmptyShould(true).evaluate(classes).hasViolation() }
}
