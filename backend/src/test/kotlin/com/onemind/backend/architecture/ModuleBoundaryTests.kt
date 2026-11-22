package com.onemind.backend.architecture

import com.onemind.backend.BackendApplication
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Mandatory in CI (ADR 0013): a failing boundary test blocks merge.
class ModuleBoundaryTests {
    private val modules = ApplicationModules.of(BackendApplication::class.java)

    private companion object {
        // JUnit makes one instance per test, so the import is shared here, not per instance.
        val classes by lazy { importApp("com.onemind.backend") }

        fun importApp(vararg packages: String): JavaClasses =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages(*packages)

        /**
         * One class per module and per layer the gates look at (relative to `com.onemind.backend`).
         * Modules without code yet are anchored by their `package-info`, which the module graph requires.
         */
        val ANCHORS =
            listOf(
                "BackendApplication",
                "account.web.AuthController",
                "account.application.RefreshTokens",
                "account.domain.IdentityProvider",
                "account.infra.persistence.VenueAccessRepository",
                "account.infra.client.IdTokenVerifier",
                "common.time.BusinessDayCalendar",
                "common.time.ClockConfig",
                "common.web.ApiPathConfig",
                "common.money.Money",
                "pos.application.PurgeOrganizationPosData",
                "pos.domain.PosMoney",
                "pos.infra.client.PosProvider",
                "pos.infra.persistence.PosOrganizationDataRepository",
                "reporting.package-info",
                "reporting.application.PurgeOrganizationReportingData",
                "reporting.infra.persistence.ReportingOrganizationDataRepository",
                "stock.package-info",
            )

        // 146 classes when this was written; the floor catches an import that lost whole areas.
        const val MIN_CLASSES = 100

        /** Why [imported] is not the whole app, or empty when the gates can trust it. */
        fun scopeGaps(imported: JavaClasses): List<String> =
            ANCHORS.filterNot { imported.contain("com.onemind.backend.$it") }.map { "missing $it" } +
                listOfNotNull("only ${imported.size} classes (< $MIN_CLASSES)".takeIf { imported.size < MIN_CLASSES })
    }

    @Test
    fun `the gates run over the whole app, so none of them passes on an empty or partial import`() {
        assertEquals(emptyList(), scopeGaps(classes))
        assertTrue(scopeGaps(importApp("com.onemind.backend.account")).isNotEmpty(), "a narrowed import must fail")
    }

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
                "pos" to listOf("common", "account::api", "account::events"),
                "reporting" to
                    listOf(
                        "common",
                        "account::api",
                        "account::events",
                        "pos::api",
                        "pos::events",
                        "stock::api",
                        "stock::events",
                    ),
                "stock" to listOf("common", "account::api", "account::events", "pos::api", "pos::events"),
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
        DatabaseAccessRules.rules().forEach { it.check(classes) }
    }

    @Test
    fun `code follows the ADR 0013 package direction and rules R2 to R9`() {
        StructureRules.all().forEach { it.check(classes) }
    }

    @Test
    fun `code keeps product rules 1, 5 and 6 (read-only POS, no float money, one clock and business-day entry point)`() {
        ProductRules.all().forEach { it.check(classes) }
    }
}
