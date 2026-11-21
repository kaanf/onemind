package com.onemind.backend.architecture

import com.onemind.backend.BackendApplication
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.Test
import kotlin.test.assertEquals

// Mandatory in CI (ADR 0013): a failing boundary test blocks merge.
class ModuleBoundaryTests {
    private val modules = ApplicationModules.of(BackendApplication::class.java)

    @Test
    fun `modules are the ones from ADR 0013`() {
        assertEquals(setOf("account", "common", "pos", "reporting", "stock"), modules.map { it.identifier.toString() }.toSet())
    }

    @Test
    fun `modules use each other only through their api packages`() {
        modules.verify()
    }

    @Test
    fun `each module declares the dependencies of the ADR 0013 module graph`() {
        val graph =
            mapOf(
                "account" to listOf("common"),
                "pos" to listOf("common", "account::api"),
                "reporting" to listOf("common", "account::api", "pos::api", "pos::events", "stock::api", "stock::events"),
                "stock" to listOf("common", "account::api", "pos::api", "pos::events"),
            )

        graph.forEach { (module, allowed) ->
            val declared =
                Class
                    .forName("com.onemind.backend.$module.package-info")
                    .getAnnotation(ApplicationModule::class.java)
                    ?.allowedDependencies
                    ?.toList()
            assertEquals(allowed, declared, module)
        }
    }

    @Test
    fun `modules use only their own jOOQ schema packages, and only from infra`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.onemind.backend")

        DatabaseAccessRules.rules().forEach { it.check(classes) }
    }

    @Test
    fun `code follows the ADR 0013 package direction and rules R2 to R9`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.onemind.backend")

        StructureRules.all().forEach { it.check(classes) }
    }

    @Test
    fun `code keeps product rules 1, 5 and 6 (read-only POS, no float money, injected Clock)`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.onemind.backend")

        ProductRules.all().forEach { it.check(classes) }
    }
}
