package com.onemind.backend.reporting.domain

import java.util.UUID

/** One venue's input to Staff: its stored staff rows of the day and the POS names it needs. */
data class VenueStaff(
    val venueId: UUID,
    val rows: List<StaffDailyRow>,
    /** POS names by staff id; a missing or null entry is a person the POS sent no name for. */
    val names: Map<UUID, String?>,
)

data class StaffSales(
    val staffId: UUID,
    val name: String?,
    val salesMinor: Long,
    val tipsMinor: Long,
    val guests: Int,
    val bills: Int,
    val deletedBills: Int,
)

data class StaffTotals(
    val salesMinor: Long,
    val tipsMinor: Long,
    val guests: Int,
    val bills: Int,
)

data class StaffFigures(
    val totals: StaffTotals,
    val staff: List<StaffSales>,
)

/**
 * Screen 03 for one business day (`docs/domain/METRICS.md`, staff). Pure.
 *
 * - Sales (paid revenue), guests, bills and deleted bills go to the person who opened the bill, tips to the
 *   person who closed it (M6); the rollups already split them so.
 * - POS staff are per account, so one person's rows in several venues of the scope are summed into one.
 * - Totals are the sums of the listed staff (a bill without an opener is in no one's figures).
 * - Sorted by sales, largest first, then by name (unnamed last), then by staff id.
 */
object StaffCalculator {
    fun compute(venues: List<VenueStaff>): StaffFigures {
        val names = venues.flatMap { it.names.entries }.filter { it.value != null }.associate { it.key to it.value }
        val staff =
            venues
                .flatMap { it.rows }
                .groupBy { it.staffId }
                .map { (staffId, rows) ->
                    StaffSales(
                        staffId = staffId,
                        name = names[staffId],
                        salesMinor = rows.sumOf { it.revenueMinor },
                        tipsMinor = rows.sumOf { it.tipsMinor },
                        guests = rows.sumOf { it.guests },
                        bills = rows.sumOf { it.bills },
                        deletedBills = rows.sumOf { it.deletedBills },
                    )
                }.sortedWith(
                    compareByDescending<StaffSales> { it.salesMinor }
                        .thenBy(nullsLast()) { it.name }
                        .thenBy { it.staffId },
                )
        return StaffFigures(
            totals =
                StaffTotals(
                    salesMinor = staff.sumOf { it.salesMinor },
                    tipsMinor = staff.sumOf { it.tipsMinor },
                    guests = staff.sumOf { it.guests },
                    bills = staff.sumOf { it.bills },
                ),
            staff = staff,
        )
    }
}
