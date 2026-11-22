package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Service
import java.util.UUID

/** What a venue's figures are missing (rule 2); a screen says so whenever any count isn't zero. */
data class ReportingDataGaps(
    /** Bills that couldn't be read at all: missing from every figure. */
    val quarantinedBills: Int,
    /** Bills stored without their lines: in bill figures, missing from product and discount figures. */
    val billsWithoutLines: Int,
    /** Bills whose newer version couldn't be read: their stored figures may be out of date. */
    val outdatedBills: Int,
    /** Bills whose payments don't add up: left out of revenue and the card/cash split. */
    val billsPaymentMismatch: Int,
)

/**
 * Reads that every reporting screen adds to its figures (rule 2). For now the data gaps, so a screen says
 * "N bills couldn't be read" when they aren't zero (phase 5). The POS gaps are read live (they change
 * without a `BillsSynced`); payment mismatches come from the rollups.
 */
@Service
class RollupReads(
    private val pos: PosReadModel,
    private val rollups: RollupRepository,
) {
    fun dataGaps(venueId: UUID): ReportingDataGaps {
        val posGaps = pos.dataGaps(venueId)
        return ReportingDataGaps(
            quarantinedBills = posGaps.quarantinedBills,
            billsWithoutLines = posGaps.billsWithoutLines,
            outdatedBills = posGaps.outdatedBills,
            billsPaymentMismatch = rollups.paymentMismatches(venueId),
        )
    }
}
