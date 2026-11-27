package com.onemind.staff.domain

import com.onemind.core.domain.money.Currency
import com.onemind.core.domain.money.Money
import kotlinx.datetime.LocalDate

/**
 * Staff figures of one business day (`docs/domain/METRICS.md`, staff). [totals] is null and [staff] empty
 * when no requested venue has data for the day ([hasData] false): no figure is shown then (rule 2).
 */
data class StaffSummary(
    /** `false` while no requested venue has data for the day (`NO_DATA_YET`). */
    val hasData: Boolean,
    val currency: Currency,
    val businessDay: LocalDate,
    /** Venues without data for the day, left out of the figures. */
    val pendingVenueCount: Int,
    val totals: StaffTotals?,
    /** Largest sales first, as sent. */
    val staff: List<StaffMember>,
    val dataGaps: StaffDataGaps,
)

/** The team's figures, the sums of the listed staff. */
data class StaffTotals(
    val sales: Money,
    val tips: Money,
    val guests: Int,
    val billCount: Int,
)

data class StaffMember(
    val staffId: String,
    /** `null` when the POS sent no name. */
    val name: String?,
    /** Paid revenue of the bills this person opened. */
    val sales: Money,
    /** Tips on the bills this person closed. */
    val tips: Money,
    val guests: Int,
    val billCount: Int,
    val deletedBillCount: Int,
)

/** Bills missing from these figures (`DataGaps` in the API). */
data class StaffDataGaps(
    val quarantinedBillCount: Int,
    val outdatedBillCount: Int,
    val paymentMismatchBillCount: Int,
) {
    val isEmpty: Boolean get() = quarantinedBillCount == 0 && outdatedBillCount == 0 && paymentMismatchBillCount == 0
}
