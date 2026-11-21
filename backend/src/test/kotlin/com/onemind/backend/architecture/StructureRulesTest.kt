package com.onemind.backend.architecture

import com.onemind.archfixture.account.api.Facade
import com.onemind.archfixture.account.api.FacadeUsingPersistence
import com.onemind.archfixture.account.api.events.SomethingHappened
import com.onemind.archfixture.account.application.BigResult
import com.onemind.archfixture.account.application.BillingService
import com.onemind.archfixture.account.application.FiveCollaborators
import com.onemind.archfixture.account.application.Outcome
import com.onemind.archfixture.account.application.SingleImplPort
import com.onemind.archfixture.account.application.SixCollaborators
import com.onemind.archfixture.account.application.UseCase
import com.onemind.archfixture.account.application.UseCaseUsingOpenApi
import com.onemind.archfixture.account.domain.DomainUsingApplication
import com.onemind.archfixture.account.domain.DomainUsingSpring
import com.onemind.archfixture.account.domain.Role
import com.onemind.archfixture.account.infra.client.Client
import com.onemind.archfixture.account.infra.client.ClientUsingPersistence
import com.onemind.archfixture.account.infra.persistence.PersistenceUsingApplication
import com.onemind.archfixture.account.infra.persistence.PersistenceUsingClient
import com.onemind.archfixture.account.infra.persistence.Repository
import com.onemind.archfixture.account.infra.persistence.RepositoryReturningRecord
import com.onemind.archfixture.account.web.Controller
import com.onemind.archfixture.account.web.ControllerUsingEvents
import com.onemind.archfixture.account.web.ControllerUsingPersistence
import com.onemind.archfixture.account.web.ControllerUsingTransactions
import com.onemind.archfixture.account.web.ControllerWithTransactionalMethod
import com.onemind.archfixture.common.events.AreaJob
import com.onemind.archfixture.common.web.CommonWebUsingTransactions
import com.onemind.archfixture.pos.infra.client.PosProvider
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves every ADR 0013 gate catches its violation and lets the allowed shape pass, using the
 * fixtures in `com.onemind.archfixture` (outside the application package, so component scanning
 * never sees their `@Service` classes).
 */
class StructureRulesTest {
    private val root = "com.onemind.archfixture"
    private val direction = StructureRules.direction(root)

    @Test
    fun `direction allows web, api to application and domain, application to infra and events, infra to domain`() {
        assertFalse(
            direction.violatedBy(Facade::class, Controller::class, UseCase::class, Repository::class, Client::class, Role::class),
        )
        assertFalse(direction.violatedBy(UseCase::class, SomethingHappened::class))
    }

    @Test
    fun `direction rejects every edge the ADR doesn't allow`() {
        listOf(
            listOf(FacadeUsingPersistence::class, Repository::class),
            listOf(ControllerUsingPersistence::class, Repository::class),
            listOf(ControllerUsingEvents::class, SomethingHappened::class),
            listOf(DomainUsingApplication::class, UseCase::class),
            listOf(PersistenceUsingClient::class, Client::class),
            listOf(PersistenceUsingApplication::class, UseCase::class),
            listOf(ClientUsingPersistence::class, Repository::class),
        ).forEach { types -> assertTrue(direction.violatedBy(*types.toTypedArray()), "not caught: ${types.first().simpleName}") }
    }

    @Test
    fun `R2 limits application beans to 5 collaborators besides Clock and TransactionTemplate, and bans Service names`() {
        val rule = StructureRules.collaborators(root)
        assertFalse(rule.violatedBy(UseCase::class, FiveCollaborators::class, BigResult::class))
        assertTrue(rule.violatedBy(SixCollaborators::class))
        assertTrue(rule.violatedBy(BillingService::class))
    }

    @Test
    fun `R4 rejects public persistence signatures with jOOQ records`() {
        val rule = StructureRules.noJooqResultsOutsidePersistence(root)
        assertFalse(rule.violatedBy(Repository::class))
        assertTrue(rule.violatedBy(RepositoryReturningRecord::class))
    }

    @Test
    fun `R5 keeps generated OpenAPI types in web`() {
        val rule = StructureRules.openApiOnlyInWeb(root, "com.onemind.api.v1")
        assertFalse(rule.violatedBy(Controller::class))
        assertTrue(rule.violatedBy(UseCaseUsingOpenApi::class))
    }

    @Test
    fun `R6 keeps Spring out of domain`() {
        val rule = StructureRules.pureDomain(root, "com.onemind.db")
        assertFalse(rule.violatedBy(Role::class, DomainUsingApplication::class))
        assertTrue(rule.violatedBy(DomainUsingSpring::class))
    }

    @Test
    fun `R7 allows transactions only in application and at a common area's root`() {
        val rules = StructureRules.transactionsOnlyInApplication(root)
        assertFalse(rules.violatedBy(UseCase::class, AreaJob::class), "allowed")
        assertTrue(rules.violatedBy(ControllerUsingTransactions::class), "TransactionTemplate in web")
        assertTrue(rules.violatedBy(ControllerWithTransactionalMethod::class), "@Transactional in web")
        assertTrue(rules.violatedBy(CommonWebUsingTransactions::class), "TransactionTemplate in common web")
    }

    @Test
    fun `R9 allows only sealed or allowlisted interfaces`() {
        val rule = StructureRules.interfacesOnlyWithSecondImplementation(root)
        assertFalse(rule.violatedBy(Outcome::class, PosProvider::class))
        assertTrue(rule.violatedBy(SingleImplPort::class))
    }

    private fun ArchRule.violatedBy(vararg types: KClass<*>): Boolean = listOf(this).violatedBy(*types)

    private fun List<ArchRule>.violatedBy(vararg types: KClass<*>): Boolean {
        val classes = ClassFileImporter().importClasses(*types.map { it.java }.toTypedArray())
        return any { it.evaluate(classes).hasViolation() }
    }
}
