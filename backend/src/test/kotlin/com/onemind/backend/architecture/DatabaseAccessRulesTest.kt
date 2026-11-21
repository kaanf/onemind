package com.onemind.backend.architecture

import com.onemind.backend.architecture.fixture.pos.application.PosServiceUsingJooq
import com.onemind.backend.architecture.fixture.reporting.infra.persistence.ReportingQueryReadingPos
import com.onemind.backend.architecture.fixture.stock.infra.client.ClientUsingJooq
import com.onemind.backend.architecture.fixture.stock.infra.persistence.StockQueryReadingPos
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
    fun `jOOQ classes outside infra persistence are rejected`() {
        assertEquals(1, violations(PosServiceUsingJooq::class))
        assertEquals(1, violations(ClientUsingJooq::class))
    }

    private fun violations(type: KClass<*>): Int {
        val classes = ClassFileImporter().importClasses(type.java)
        return rules.count { it.allowEmptyShould(true).evaluate(classes).hasViolation() }
    }
}
