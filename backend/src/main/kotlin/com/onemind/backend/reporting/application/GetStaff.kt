package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.StaffCalculator
import com.onemind.backend.reporting.domain.StaffFigures
import com.onemind.backend.reporting.domain.VenueStaff
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

data class StaffView(
    val currency: Currency,
    /** The day shown. */
    val businessDay: LocalDate,
    /** The venues' current business day (one clock). */
    val today: LocalDate,
    val timeZone: String,
    val venues: List<ScopedVenue>,
    /** The venues with data for [businessDay]: synced, and their data starts on or before it. */
    val coveredVenueIds: Set<UUID>,
    /** Null when no requested venue has data for the day; an empty list on a real day without bills. */
    val figures: StaffFigures?,
    val gaps: TodayGapsView,
)

/**
 * Screen 03: sales, tips, guests and bills per staff member on one business day (`docs/domain/METRICS.md`,
 * staff). Venues and covered days as on Products by hour ([GetProducts]); figures from the stored staff
 * rows, names from the POS ([PosReadModel.staff]). No hours or shifts: the POS API has no sign-ins.
 */
@Service
class GetStaff(
    private val scopes: DashboardScopes,
    private val rollups: RollupRepository,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
        date: LocalDate?,
    ): StaffView {
        val scope = scopes.resolve(userId, venueIds)
        val today = scope.businessDay(clock.instant())
        val day = date ?: today
        if (day.isAfter(today)) throw SalesDateInFutureException()

        // A day before a venue's data starts is no data for it (rule 2), not a day without bills.
        val covered = scope.ready.filter { venue -> rollups.firstDay(venue.venueId)?.let { !it.isAfter(day) } == true }
        val inputs =
            covered.map { venue ->
                val rows = rollups.staffDaily(venue.venueId, day)
                val names = pos.staff(venue.venueId, rows.map { it.staffId })
                VenueStaff(venue.venueId, rows, names.associate { it.staffId to it.name })
            }
        val figures = if (inputs.isEmpty()) null else StaffCalculator.compute(inputs)
        val posGaps = covered.map { pos.dataGaps(it.venueId, day..day) }
        return StaffView(
            currency = scope.currency,
            businessDay = day,
            today = today,
            timeZone = scope.zone.id,
            venues = scope.venues,
            coveredVenueIds = covered.map { it.venueId }.toSet(),
            figures = figures,
            gaps =
                TodayGapsView(
                    quarantinedBills = posGaps.sumOf { it.quarantinedBills },
                    // Staff figures are bill figures: a bill stored without lines still counts here.
                    billsWithoutLines = 0,
                    outdatedBills = posGaps.sumOf { it.outdatedBills },
                    paymentMismatchBills = covered.sumOf { rollups.hours(it.venueId, listOf(day)).sumOf { h -> h.paymentMismatchBills } },
                ),
        )
    }
}
