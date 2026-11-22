package com.onemind.backend.account.application

import com.onemind.backend.account.api.events.OrganizationPurging
import com.onemind.backend.account.infra.persistence.OrganizationPurgeRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Deletes organizations whose `deletion_due_at` has passed, with all their data
 * (`docs/architecture/DB_SCHEMA.md`, account deletion). One transaction per organization: the other
 * modules delete their rows in their [OrganizationPurging] listeners, then the venues and the
 * organization go. An organization that fails stays marked and is tried again on the next run.
 */
@Service
class PurgeDueOrganizations(
    private val organizations: OrganizationPurgeRepository,
    private val events: ApplicationEventPublisher,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Purges every organization due at [now] in batches of [BATCH_SIZE]; returns how many went. */
    fun run(now: Instant = clock.instant()): Int {
        val failed = mutableSetOf<UUID>()
        var purged = 0
        while (true) {
            val due = organizations.findDue(now, BATCH_SIZE, failed)
            if (due.isEmpty()) return purged
            for (organizationId in due) {
                try {
                    if (purge(organizationId, now)) purged++ else failed += organizationId
                } catch (e: RuntimeException) {
                    failed += organizationId
                    log.error("Organization purge failed: organizationId={}", organizationId, e)
                }
            }
        }
    }

    private fun purge(
        organizationId: UUID,
        now: Instant,
    ): Boolean =
        transactions.execute {
            if (!organizations.lockIfDue(organizationId, now)) return@execute false
            val venueIds = organizations.venueIds(organizationId)
            events.publishEvent(OrganizationPurging(organizationId, venueIds))
            organizations.deleteVenuesAndOrganization(organizationId)
            log.info("Organization purged: organizationId={}, venues={}", organizationId, venueIds.size)
            true
        } == true

    companion object {
        const val TASK = "purge-due-organizations"
        const val BATCH_SIZE = 20
    }
}
