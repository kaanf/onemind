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

class BusinessDaysDifferException :
    RuleViolationException(
        code = "BUSINESS_DAYS_DIFFER",
        detail = "These venues don't share one time zone and cut-off, so their hours can't share one chart.",
    )

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
        require(ids.isNotEmpty()) { "at least one venue (the API validates it)" }
        ids.forEach { venueAccess.requireAccess(userId, it) }
        val settings = ids.associateWith { requireNotNull(venueRegistry.businessDay(it)) { "an accessible venue has settings" } }
        val currency =
            settings.values
                .map { it.currency }
                .distinct()
                .singleOrNull() ?: throw CurrenciesDifferException()
        // One chart, one clock: venues on different zones or cut-offs are a first-release limit (rule 6).
        if (settings.values
                .map { it.zone to it.businessDayStart }
                .distinct()
                .size > 1
        ) {
            throw BusinessDaysDifferException()
        }
        val now = clock.instant()

        val venues =
            ids.map { id ->
                val venue = settings.getValue(id)
                val dataAsOf = pos.lastBillsSyncAt(id)
                TodayVenueView(id, today(venue, now), venue.zone.id, ready = dataAsOf != null, dataAsOf = dataAsOf)
            }
        val ready = venues.filter { it.ready }
        val shared = settings.getValue(venues.first().venueId)
        val currentHour = BusinessDayCalendar.localHour(now, shared.zone)
        val dayHours = BusinessDayCalendar.localHours(venues.first().businessDay, shared.zone, shared.businessDayStart)

        val inputs = ready.associateWith { venue -> venueToday(venue) }
        val figures = if (ready.isEmpty()) null else TodayCalculator.compute(inputs.values.toList(), currentHour, dayHours)
        return TodayView(
            currency = currency,
            venues = venues,
            figures = figures,
            comparison = comparison(inputs, shared, now),
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

    /**
     * Paid so far against the same weekday last week up to the same local time, over the same venues: only
     * those that have that day (a venue synced since would make up growth). `null` when none has it.
     */
    private fun comparison(
        inputs: Map<TodayVenueView, VenueToday>,
        shared: VenueBusinessDay,
        now: Instant,
    ): TodayComparisonView? {
        val cut = BusinessDayCalendar.sameLocalTimeDaysEarlier(now, shared.zone, DAYS_IN_WEEK)
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
