package com.onemind.backend.architecture

import com.onemind.backend.architecture.fixture.pos.application.PosServiceUsingJooq
import com.onemind.backend.architecture.fixture.reporting.infra.ReportingQueryReadingPos
import com.onemind.backend.architecture.fixture.stock.infra.StockQueryReadingPos
import com.tngtech.archunit.core.importer.ClassFileImporter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

// Proves the rules catch violations, using fixture classes under architecture.fixture.
class DatabaseAccessRulesTest {
    private val rules = DatabaseAccessRules.rules(appRoot = "com.onemind.backend.architecture.fixture")

    @Test
    fun `reporting may not read pos tables`() {
        assertEquals(1, violations(ReportingQueryReadingPos::class))
    }

    @Test
    fun `stock may not read pos tables`() {
        assertEquals(1, violations(StockQueryReadingPos::class))
    }

    @Test
    fun `jOOQ classes outside infra are rejected`() {
        assertEquals(1, violations(PosServiceUsingJooq::class))
    }

    private fun violations(type: KClass<*>): Int {
        val classes = ClassFileImporter().importClasses(type.java)
        return rules.count { it.evaluate(classes).hasViolation() }
    }
}
