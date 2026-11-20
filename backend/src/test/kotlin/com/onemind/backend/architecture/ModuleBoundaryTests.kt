package com.onemind.backend.architecture

import com.onemind.backend.BackendApplication
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.Test
import kotlin.test.assertEquals

// Mandatory in CI (ADR 0009): a failing boundary test blocks merge.
class ModuleBoundaryTests {
    private val modules = ApplicationModules.of(BackendApplication::class.java)

    @Test
    fun `modules are the ones from ADR 0009`() {
        assertEquals(setOf("account", "common", "pos", "reporting", "stock"), modules.map { it.identifier.toString() }.toSet())
    }

    @Test
    fun `modules use each other only through their api packages`() {
        modules.verify()
    }

    @Test
    fun `modules use only their own jOOQ schema packages, and only from infra`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.onemind.backend")

        DatabaseAccessRules.rules().forEach { it.check(classes) }
    }
}
