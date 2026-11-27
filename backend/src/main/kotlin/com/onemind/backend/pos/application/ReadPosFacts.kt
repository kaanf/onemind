package com.onemind.backend.pos.application

import com.onemind.backend.pos.domain.StoredBill
import com.onemind.backend.pos.domain.StoredBillLine
import com.onemind.backend.pos.domain.StoredCategory
import com.onemind.backend.pos.domain.StoredProduct
import com.onemind.backend.pos.domain.StoredStaff
import com.onemind.backend.pos.infra.persistence.PosReadRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What a venue's data is missing: bills not stored at all, and bills stored without their lines. */
data class PosDataGaps(
    val quarantinedBills: Int,
    val billsWithoutLines: Int,
    val outdatedBills: Int,
)

/**
 * POS facts for other modules, reached through [PosReadModel][com.onemind.backend.pos.api.PosReadModel]
 * (ADR 0013 §5): pages ordered by row ID, read-only.
 */
@Service
class ReadPosFacts(
    private val reads: PosReadRepository,
) {
    fun bills(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID?,
        limit: Int,
    ): List<StoredBill> = reads.bills(venueId, businessDays, after, limit.coerceIn(1, MAX_PAGE))

    fun billLines(
        venueId: UUID,
        businessDays: Collection<LocalDate>,
        after: UUID?,
        limit: Int,
    ): List<StoredBillLine> = reads.billLines(venueId, businessDays, after, limit.coerceIn(1, MAX_PAGE))

    fun products(
        venueId: UUID,
        productIds: Collection<UUID>,
    ): List<StoredProduct> = if (productIds.isEmpty()) emptyList() else reads.products(venueId, productIds)

    fun categories(venueId: UUID): List<StoredCategory> = reads.categories(venueId)

    fun staff(
        venueId: UUID,
        staffIds: Collection<UUID>,
    ): List<StoredStaff> = if (staffIds.isEmpty()) emptyList() else reads.staff(venueId, staffIds)

    fun syncedVenueIds(): List<UUID> = reads.syncedVenueIds()

    fun businessDays(venueId: UUID): List<LocalDate> = reads.businessDays(venueId)

    /** Gaps of the venue; lineless and outdated bills of [day] only when given (quarantined ones have no day). */
    fun dataGaps(
        venueId: UUID,
        days: ClosedRange<LocalDate>? = null,
    ): PosDataGaps =
        PosDataGaps(reads.quarantinedBills(venueId), reads.billsWithoutLines(venueId, days), reads.outdatedBills(venueId, days))

    fun lastBillsSyncAt(venueId: UUID): Instant? = reads.lastBillsSyncAt(venueId)

    companion object {
        const val MAX_PAGE = 1000
    }
}
