package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.events.BillsSynced
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Refreshes the rollups of the business days a sync wrote (ADR 0013, events), the previous day of a
 * moved bill included. Delivery is at least once; [RefreshRollups] replaces whole days, so a repeat is
 * harmless.
 */
@Component
class RollupListener(
    private val refresh: RefreshRollups,
) {
    @ApplicationModuleListener
    fun on(event: BillsSynced) {
        refresh.refresh(event.venueId, event.businessDays)
    }
}
