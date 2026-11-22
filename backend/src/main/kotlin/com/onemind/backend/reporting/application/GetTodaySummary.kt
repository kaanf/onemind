package com.onemind.backend.reporting.application

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.BillRevenue
import com.onemind.backend.reporting.domain.TodayCalculator
import com.onemind.backend.reporting.domain.TodayFigures
import com.onemind.backend.reporting.domain.VenueToday
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

/** One requested venue: its business day and whether it has data yet (rule 2). */
data class TodayVenueView(
    val venueId: UUID,
    val businessDay: LocalDate,
    val timeZone: String,
    val ready: Boolean,
    val dataAsOf: Instant?,
)

/** Paid so far of [venueIds] against the same venues last week; venues without that day are left out of both. */
data class TodayComparisonView(
    val previousBusinessDay: LocalDate,
    val venueIds: List<UUID>,
    val currentMinor: Long,
    val previousMinor: Long,
    val deltaPercent: BigDecimal?,
)

data class TodayGapsView(
    val quarantinedBills: Int,
    val billsWithoutLines: Int,
    val outdatedBills: Int,
    val paymentMismatchBills: Int,
)

data class TodayView(
    val currency: Currency,
    val venues: List<TodayVenueView>,
    /** Null when no requested venue has data yet. */
    val figures: TodayFigures?,
    val comparison: TodayComparisonView?,
    val currentHour: Int,
    val gaps: TodayGapsView,
)

/**
 * Screen 01 for one or more venues (`docs/domain/METRICS.md`; rule 7). Every venue is access-checked
 * first. Figures sum the venues that have synced at least once; the others are listed as pending, never
 * counted as 0 (rule 2). Each venue's business day is its own (zone and cut-off, rule 6). Amounts of
 * different currencies are never added up: `422 CURRENCIES_DIFFER`.
 *
 * The comparison is "same weekday last week, up to the same local time", over the venues that have
 * that day: hourly rollups can't cut at that minute, so the earlier figure comes from that day's POS
 * facts with the one revenue rule ([BillRevenue.paid]). Venues on different zones or cut-offs are
 * `422 BUSINESS_DAYS_DIFFER` (one chart, one clock; a first-release limit).
 */
@Service
class GetTodaySummary(
    private val scopes: DashboardScopes,
    private val rollups: RollupRepository,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
    ): TodayView {
        val scope = scopes.resolve(userId, venueIds)
        val now = clock.instant()
        val businessDay = scope.businessDay(now)
        val venues = scope.venues.map { TodayVenueView(it.venueId, businessDay, scope.zone.id, it.ready, it.dataAsOf) }
        val ready = venues.filter { it.ready }
        val currentHour = BusinessDayCalendar.localHour(now, scope.zone)
        val dayHours = BusinessDayCalendar.localHours(businessDay, scope.zone, scope.businessDayStart)

        val inputs = ready.associateWith { venue -> venueToday(venue) }
        val figures = if (ready.isEmpty()) null else TodayCalculator.compute(inputs.values.toList(), currentHour, dayHours)
        return TodayView(
            currency = scope.currency,
            venues = venues,
            figures = figures,
            comparison = comparison(inputs, scope, now),
            currentHour = currentHour,
            gaps = gaps(ready, figures),
        )
    }

    private fun venueToday(venue: TodayVenueView): VenueToday {
        val sameWeekdays = (1L..SAME_WEEKDAYS).map { venue.businessDay.minusWeeks(it) }
        val withData = rollups.daysWithRollups(venue.venueId, sameWeekdays)
        val earlier = rollups.hours(venue.venueId, withData).groupBy { it.day }
        return VenueToday(
            today = rollups.hours(venue.venueId, listOf(venue.businessDay)),
            sameWeekdays = withData.associateWith { earlier[it].orEmpty() },
        )
    }

    /**
     * Paid so far against the same weekday last week up to the same local time, over the same venues: only
     * those that have that day (a venue synced since would make up growth). `null` when none has it.
     */
    private fun comparison(
        inputs: Map<TodayVenueView, VenueToday>,
        scope: DashboardScope,
        now: Instant,
    ): TodayComparisonView? {
        val cut = BusinessDayCalendar.sameLocalTimeDaysEarlier(now, scope.zone, DAYS_IN_WEEK)
        val compared =
            inputs.filterKeys { venue -> rollups.daysWithRollups(venue.venueId, listOf(venue.businessDay.minusWeeks(1))).isNotEmpty() }
        if (compared.isEmpty()) return null
        val previous =
            compared.keys.sumOf { venue ->
                val day = venue.businessDay.minusWeeks(1)
                val bills = allPages { after -> pos.bills(venue.venueId, listOf(day), after).let { it.items to it.next } }
                BillRevenue.paid(bills.filter { it.paidAt.isBefore(cut) }.map { it.toRollup() })
            }
        val current = compared.values.sumOf { venue -> venue.today.sumOf { it.paidMinor } }
        return TodayComparisonView(
            previousBusinessDay =
                compared.keys
                    .first()
                    .businessDay
                    .minusWeeks(1),
            venueIds = compared.keys.map { it.venueId },
            currentMinor = current,
            previousMinor = previous,
            deltaPercent = TodayCalculator.deltaPercent(current, previous),
        )
    }

    /** Today's gaps of the venues with data; quarantined bills have no stored day, so all of them count. */
    private fun gaps(
        ready: List<TodayVenueView>,
        figures: TodayFigures?,
    ): TodayGapsView {
        val pos = ready.map { pos.dataGaps(it.venueId, it.businessDay..it.businessDay) }
        return TodayGapsView(
            quarantinedBills = pos.sumOf { it.quarantinedBills },
            billsWithoutLines = pos.sumOf { it.billsWithoutLines },
            outdatedBills = pos.sumOf { it.outdatedBills },
            paymentMismatchBills = figures?.paymentMismatchBills ?: 0,
        )
    }

    private companion object {
        const val SAME_WEEKDAYS = 4L
        const val DAYS_IN_WEEK = 7L
    }
}
