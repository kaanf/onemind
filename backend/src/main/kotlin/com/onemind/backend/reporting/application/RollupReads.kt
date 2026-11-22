package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.DataGaps
import com.onemind.backend.pos.api.PosReadModel
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Reads that every reporting screen adds to its figures (rule 2). For now the data gaps: bills that
 * couldn't be read are missing from the rollups, so a screen says "N bills couldn't be read" when this
 * isn't zero (phase 5). Read live, not rolled up: it changes without a `BillsSynced`.
 */
@Service
class RollupReads(
    private val pos: PosReadModel,
) {
    fun dataGaps(venueId: UUID): DataGaps = pos.dataGaps(venueId)
}
