package com.onemind.backend.reporting.application

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.HourTotals
import com.onemind.backend.reporting.domain.RollupCalculator
import com.onemind.backend.reporting.domain.SalesPeriod
import com.onemind.backend.reporting.domain.SalesRanges
import com.onemind.backend.reporting.domain.toTotals
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The previous side of a period comparison (`docs/domain/METRICS.md`, periods): which venues count and
 * the hours of the compared span. Whole days come from the stored hours; the cut day from that day's POS
 * facts up to the same local time as the venue's figures reach (its last bills sync, not now: between
 * syncs the current side lags), run through [RollupCalculator] so every figure follows the rollup rules.
 */
@Component
class ComparedSpans(
    private val rollups: RollupRepository,
    private val pos: PosReadModel,
) {
    /** Of [venueIds], those whose data (first rollup day) starts by the compared span's first day. */
    fun venues(
        venueIds: Collection<UUID>,
        ranges: SalesRanges,
    ): List<UUID> = venueIds.filter { venueId -> rollups.firstDay(venueId)?.let { !it.isAfter(ranges.comparedFrom) } == true }

    /** The compared span's hours of [venueId]; [dataAsOf] is its last bills sync. */
    fun hours(
        venueId: UUID,
        ranges: SalesRanges,
        scope: DashboardScope,
        today: LocalDate,
        now: Instant,
        dataAsOf: Instant,
    ): List<HourTotals> {
        val whole = rollups.hours(venueId, ranges.comparedWholeDays)
        val cutDay = ranges.comparedCutDay ?: return whole
        val daysBack = ChronoUnit.DAYS.between(cutDay, today)
        val cut = BusinessDayCalendar.sameLocalTimeDaysEarlier(minOf(now, dataAsOf), scope.zone, daysBack)
        // HOUR: the previous side also starts at the start of the same hour.
        val hourStart =
            if (ranges.period == SalesPeriod.HOUR) {
                BusinessDayCalendar.sameLocalTimeDaysEarlier(BusinessDayCalendar.startOfLocalHour(now, scope.zone), scope.zone, daysBack)
            } else {
                null
            }
        val bills =
            allPages { after -> pos.bills(venueId, listOf(cutDay), after).let { it.items to it.next } }
                .filter { it.paidAt.isBefore(cut) && (hourStart == null || !it.paidAt.isBefore(hourStart)) }
                .map { it.toRollup() }
        val range = BusinessDayCalendar.range(cutDay, scope.zone, scope.businessDayStart)
        return whole + RollupCalculator.compute(bills, emptyList(), range, scope.zone).hourlySales.map { it.toTotals(cutDay) }
    }
}
