package com.onemind.backend.reporting.application

import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.account.api.VenueBusinessDay
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.common.error.RuleViolationException
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

class CurrenciesDifferException :
    RuleViolationException(
        code = "CURRENCIES_DIFFER",
        detail = "These venues don't share one currency, so their amounts can't be added up.",
    )

/** One requested venue: its business day and whether it has data yet (rule 2). */
data class TodayVenueView(
    val venueId: UUID,
    val businessDay: LocalDate,
    val timeZone: String,
    val ready: Boolean,
    val dataAsOf: Instant?,
)

data class TodayComparisonView(
    val previousBusinessDay: LocalDate,
    val previousMinor: Long?,
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
 * The comparison is "same weekday last week, up to the same local time": hourly rollups can't cut at
 * that minute, so the earlier figure comes from that day's POS facts with the one revenue rule
 * ([BillRevenue.paid]).
 */
@Service
class GetTodaySummary(
    private val venueAccess: VenueAccess,
    private val venueRegistry: VenueRegistry,
    private val rollups: RollupRepository,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
    ): TodayView {
        val ids = venueIds.distinct()
        ids.forEach { venueAccess.requireAccess(userId, it) }
        val settings = ids.associateWith { requireNotNull(venueRegistry.businessDay(it)) { "an accessible venue has settings" } }
        val currency =
            settings.values
                .map { it.currency }
                .distinct()
                .singleOrNull() ?: throw CurrenciesDifferException()
        val now = clock.instant()

        val venues =
            ids.map { id ->
                val venue = settings.getValue(id)
                val dataAsOf = pos.lastBillsSyncAt(id)
                TodayVenueView(id, today(venue, now), venue.zone.id, ready = dataAsOf != null, dataAsOf = dataAsOf)
            }
        val ready = venues.filter { it.ready }
        // The hour axis follows the first venue with data (venues usually share a zone and cut-off).
        val axis = settings.getValue((ready.firstOrNull() ?: venues.first()).venueId)
        val currentHour = BusinessDayCalendar.localHour(now, axis.zone)

        val inputs = ready.map { venue -> venueToday(venue) }
        val figures = if (ready.isEmpty()) null else TodayCalculator.compute(inputs, currentHour, axis.businessDayStart.hour)
        return TodayView(
            currency = currency,
            venues = venues,
            figures = figures,
            comparison = figures?.let { comparison(ready, settings, now, it.paidMinor) },
            currentHour = currentHour,
            gaps = gaps(ready, figures),
        )
    }

    private fun today(
        venue: VenueBusinessDay,
        now: Instant,
    ): LocalDate = BusinessDayCalendar.businessDate(now, venue.zone, venue.businessDayStart)

    private fun venueToday(venue: TodayVenueView): VenueToday {
        val sameWeekdays = (1L..SAME_WEEKDAYS).map { venue.businessDay.minusWeeks(it) }
        val withData = rollups.daysWithRollups(venue.venueId, sameWeekdays)
        val earlier = rollups.hours(venue.venueId, withData).groupBy { it.day }
        return VenueToday(
            today = rollups.hours(venue.venueId, listOf(venue.businessDay)),
            sameWeekdays = withData.associateWith { earlier[it].orEmpty() },
        )
    }

    /** Paid so far against the same weekday last week up to the same local time, from that day's POS facts. */
    private fun comparison(
        ready: List<TodayVenueView>,
        settings: Map<UUID, VenueBusinessDay>,
        now: Instant,
        paidMinor: Long,
    ): TodayComparisonView {
        val previous =
            ready.mapNotNull { venue ->
                val previousDay = venue.businessDay.minusWeeks(1)
                if (rollups.daysWithRollups(venue.venueId, listOf(previousDay)).isEmpty()) return@mapNotNull null
                val cut = BusinessDayCalendar.sameLocalTimeDaysEarlier(now, settings.getValue(venue.venueId).zone, DAYS_IN_WEEK)
                val bills = allPages { after -> pos.bills(venue.venueId, listOf(previousDay), after).let { it.items to it.next } }
                BillRevenue.paid(bills.filter { it.paidAt.isBefore(cut) }.map { it.toRollup() })
            }
        val previousMinor = if (previous.isEmpty()) null else previous.sum()
        return TodayComparisonView(
            previousBusinessDay = ready.first().businessDay.minusWeeks(1),
            previousMinor = previousMinor,
            deltaPercent = TodayCalculator.deltaPercent(paidMinor, previousMinor),
        )
    }

    /** Today's gaps of the venues with data; quarantined bills have no stored day, so all of them count. */
    private fun gaps(
        ready: List<TodayVenueView>,
        figures: TodayFigures?,
    ): TodayGapsView {
        val pos = ready.map { pos.dataGaps(it.venueId, it.businessDay) }
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
