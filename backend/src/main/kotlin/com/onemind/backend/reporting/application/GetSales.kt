package com.onemind.backend.reporting.application

import com.onemind.backend.common.error.InvalidRequestException
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.BillRevenue
import com.onemind.backend.reporting.domain.HourTotals
import com.onemind.backend.reporting.domain.RollupVersion
import com.onemind.backend.reporting.domain.SalesCalculator
import com.onemind.backend.reporting.domain.SalesFigures
import com.onemind.backend.reporting.domain.SalesPeriod
import com.onemind.backend.reporting.domain.SalesRanges
import com.onemind.backend.reporting.domain.TodayCalculator
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.UUID

class SalesPeriodUnknownException :
    InvalidRequestException(
        code = "SALES_PERIOD_UNKNOWN",
        detail = "The period is not one of HOUR, DAY, WEEK or MONTH.",
    )

class SalesDateInFutureException :
    InvalidRequestException(
        code = "SALES_DATE_IN_FUTURE",
        detail = "The date is after the venues' current business day.",
    )

class SalesHourNotTodayException :
    InvalidRequestException(
        code = "SALES_HOUR_NOT_TODAY",
        detail = "The HOUR period is the current clock hour, so it takes no other date than today.",
    )

/** Revenue of [venueIds] against the compared span of the previous period, over the same venues. */
data class SalesComparisonView(
    val from: LocalDate,
    val to: LocalDate,
    /** True when the last compared day is cut at the same local time as each venue's last sync (an unfinished period). */
    val upToSameTime: Boolean,
    val venueIds: List<UUID>,
    val currentMinor: Long,
    val previousMinor: Long,
    val deltaPercent: BigDecimal?,
)

data class SalesView(
    val currency: Currency,
    /** The venues' current business day and time zone (one clock, [DashboardScopes]). */
    val businessDay: LocalDate,
    val timeZone: String,
    val venues: List<ScopedVenue>,
    val ranges: SalesRanges,
    /** The current local hour for `HOUR`, else null. */
    val hour: Int?,
    /** Null when no requested venue has data yet. */
    val figures: SalesFigures?,
    val comparison: SalesComparisonView?,
    val gaps: TodayGapsView,
)

/**
 * Screen 02 for one or more venues and a period (`docs/domain/METRICS.md`, periods; rule 7). Venues are
 * resolved like Today ([DashboardScopes]); figures sum the venues with data, the others are pending.
 *
 * Figures come from the stored hours of the period's days up to today (for `HOUR`: the current hour of
 * today). The comparison takes the compared span of the previous period ([SalesRanges], M3 default):
 * whole days from the stored hours, the cut day from that day's POS facts up to the same local time as
 * each venue's figures reach, its last bills sync (hourly rollups can't cut inside an hour), with the one revenue rule ([BillRevenue.paid]). Only venues
 * whose data starts by the compared span's first day count in it, in both figures, so a venue synced
 * since makes up no growth; none: no comparison.
 */
@Service
class GetSales(
    private val scopes: DashboardScopes,
    private val rollups: RollupRepository,
    private val comparedSpans: ComparedSpans,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
        period: SalesPeriod,
        date: LocalDate?,
    ): SalesView {
        val scope = scopes.resolve(userId, venueIds)
        val now = clock.instant()
        val today = scope.businessDay(now)
        val anchor = date ?: today
        if (anchor.isAfter(today)) throw SalesDateInFutureException()
        if (period == SalesPeriod.HOUR && anchor != today) throw SalesHourNotTodayException()
        val ranges = SalesRanges.of(period, anchor, today)
        val hour = if (period == SalesPeriod.HOUR) BusinessDayCalendar.localHour(now, scope.zone) else null
        val days = ranges.elapsedDays(today)

        val current = scope.ready.associate { it.venueId to currentHours(it.venueId, days, hour) }
        val upToDate = scope.ready.none { rollups.outdatedDays(it.venueId, days, RollupVersion.CURRENT).isNotEmpty() }
        val figures = if (scope.ready.isEmpty()) null else SalesCalculator.compute(current.values.flatten(), upToDate)
        return SalesView(
            currency = scope.currency,
            businessDay = today,
            timeZone = scope.zone.id,
            venues = scope.venues,
            ranges = ranges,
            hour = hour,
            figures = figures,
            comparison = comparison(scope, ranges, current, now, today),
            gaps = gaps(scope, days, figures),
        )
    }

    private fun currentHours(
        venueId: UUID,
        days: List<LocalDate>,
        hour: Int?,
    ): List<HourTotals> = rollups.hours(venueId, days).filter { hour == null || it.hour == hour }

    private fun comparison(
        scope: DashboardScope,
        ranges: SalesRanges,
        current: Map<UUID, List<HourTotals>>,
        now: Instant,
        today: LocalDate,
    ): SalesComparisonView? {
        val compared = comparedSpans.venues(current.keys, ranges)
        if (compared.isEmpty()) return null
        val dataAsOf = scope.ready.associate { it.venueId to requireNotNull(it.dataAsOf) }
        val previous =
            compared.sumOf { venueId ->
                comparedSpans.hours(venueId, ranges, scope, today, now, dataAsOf.getValue(venueId)).sumOf { it.paidMinor }
            }
        val currentMinor = compared.sumOf { venueId -> current.getValue(venueId).sumOf { it.paidMinor } }
        return SalesComparisonView(
            from = ranges.comparedFrom,
            to = ranges.comparedTo,
            upToSameTime = ranges.comparedCutDay != null,
            venueIds = compared,
            currentMinor = currentMinor,
            previousMinor = previous,
            deltaPercent = TodayCalculator.deltaPercent(currentMinor, previous),
        )
    }

    /** Gaps of the venues with data over the period's elapsed days; quarantined bills have no stored day, so all count. */
    private fun gaps(
        scope: DashboardScope,
        days: List<LocalDate>,
        figures: SalesFigures?,
    ): TodayGapsView {
        val pos = scope.ready.map { pos.dataGaps(it.venueId, days.first()..days.last()) }
        return TodayGapsView(
            quarantinedBills = pos.sumOf { it.quarantinedBills },
            billsWithoutLines = pos.sumOf { it.billsWithoutLines },
            outdatedBills = pos.sumOf { it.outdatedBills },
            paymentMismatchBills = figures?.paymentMismatchBills ?: 0,
        )
    }
}
