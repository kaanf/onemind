package com.onemind.backend.pos.application

import com.onemind.backend.account.api.events.OrganizationPurging
import com.onemind.backend.pos.infra.persistence.PosOrganizationDataRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Deletes an organization's POS read model inside the account purge transaction ([OrganizationPurging]). */
@Component
class PurgeOrganizationPosData(
    private val posData: PosOrganizationDataRepository,
) {
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun on(event: OrganizationPurging) {
        posData.deleteAll(event.organizationId, event.venueIds)
    }
}
