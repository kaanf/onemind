package com.onemind.backend.reporting.application

import com.onemind.backend.account.api.events.OrganizationPurging
import com.onemind.backend.reporting.infra.persistence.ReportingOrganizationDataRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Deletes an organization's rollups inside the account purge transaction ([OrganizationPurging]). */
@Component
class PurgeOrganizationReportingData(
    private val reportingData: ReportingOrganizationDataRepository,
) {
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun on(event: OrganizationPurging) {
        reportingData.deleteAll(event.venueIds)
    }
}
