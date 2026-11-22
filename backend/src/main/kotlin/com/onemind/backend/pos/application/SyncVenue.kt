package com.onemind.backend.pos.application

import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.SyncJobRepository
import com.onemind.backend.pos.infra.persistence.SyncStepRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Currency
import java.util.UUID

/**
 * Everything of one active venue in a sync run: menu and desks when due ([SyncCatalog]), then bills
 * ([SyncBills]). A failing bills sync is a failed step of this venue only; the cursor keeps what was
 * written, so the next run resumes there.
 */
@Service
class SyncVenue(
    private val syncCatalog: SyncCatalog,
    private val syncBills: SyncBills,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run(
        connectionId: UUID,
        venue: LinkedVenue,
        place: PosPlaceRef,
        currency: Currency,
        provider: PosProvider,
    ): List<SyncStepRow> {
        val catalog = syncCatalog.runIfDue(venue, place, currency, provider)
        val name = "bills:${venue.venueId}"
        val bills =
            try {
                syncBills.run(connectionId, venue, place, provider)
                SyncStepRow(name, SyncJobRepository.SUCCEEDED)
            } catch (e: RuntimeException) {
                val error = e.javaClass.simpleName
                log.warn("POS bills sync failed: venueId={}, error={}", venue.venueId, error, e)
                SyncStepRow(name, SyncJobRepository.FAILED, error)
            }
        return catalog + bills
    }
}
