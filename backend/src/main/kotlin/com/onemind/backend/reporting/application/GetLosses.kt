package com.onemind.backend.reporting.application

import com.onemind.backend.common.error.InvalidRequestException
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.LossCalculator
import com.onemind.backend.reporting.domain.LossFigures
import com.onemind.backend.reporting.domain.RollupVersion
import com.onemind.backend.reporting.domain.SalesPeriod
import com.onemind.backend.reporting.domain.SalesRanges
import com.onemind.backend.reporting.domain.VenueLosses
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

class LossesHourUnsupportedException :
    InvalidRequestException(
        code = "LOSSES_HOUR_UNSUPPORTED",
        detail = "Discounts & losses cover days: DAY, WEEK or MONTH.",
    )

/** The loss share of [venueIds] against the compared span of the previous period, over the same venues. */
data class LossComparisonView(
    val from: LocalDate,
    val to: LocalDate,
    val upToSameTime: Boolean,
    val venueIds: List<UUID>,
    val currentSharePercent: BigDecimal?,
    val previousSharePercent: BigDecimal?,
    val changePoints: BigDecimal?,
)

data class LossesView(
    val currency: Currency,
    val businessDay: LocalDate,
    val timeZone: String,
    val venues: List<ScopedVenue>,
    val ranges: SalesRanges,
    /** Null when no requested venue has data yet. */
    val figures: LossFigures?,
    val comparison: LossComparisonView?,
    val gaps: TodayGapsView,
)

/**
 * Screen 02c for one or more venues and a period (`docs/domain/METRICS.md`, discounts and losses). Periods,
 * venues and the comparison follow Sales ([GetSales], [ComparedSpans]); the loss share's change is in
 * percentage points. Version-dependent figures (record counts) are null while a day of the period is still
 * stored by an older calculation (rule 2). A day whose typed discounts are above its bill-level ones is a
 * data gap: logged and counted, never clamped.
 */
@Service
class GetLosses(
    private val scopes: DashboardScopes,
    private val rollups: RollupRepository,
    private val comparedSpans: ComparedSpans,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
        period: SalesPeriod,
        date: LocalDate?,
    ): LossesView {
        val scope = scopes.resolve(userId, venueIds)
        val now = clock.instant()
        val today = scope.businessDay(now)
        val anchor = date ?: today
        if (anchor.isAfter(today)) throw SalesDateInFutureException()
        if (period == SalesPeriod.HOUR) throw LossesHourUnsupportedException()
        val ranges = SalesRanges.of(period, anchor, today)
        val days = ranges.elapsedDays(today)

        val inputs =
            scope.ready.map { venue ->
                VenueLosses(
                    venueId = venue.venueId,
                    hours = rollups.hours(venue.venueId, days),
                    typed = rollups.typedDiscounts(venue.venueId, days),
                )
            }
        val upToDate = scope.ready.none { rollups.outdatedDays(it.venueId, days, RollupVersion.CURRENT).isNotEmpty() }
        val figures = if (inputs.isEmpty()) null else LossCalculator.compute(inputs, upToDate)
        figures?.discountMismatches?.forEach {
            log.warn("Typed discounts above bill-level discounts: venueId={}, businessDay={}", it.venueId, it.day)
        }

        val compared = comparedSpans.venues(scope.ready.map { it.venueId }, ranges)
        val comparison =
            if (compared.isEmpty()) {
                null
            } else {
                val current = inputs.filter { it.venueId in compared }.flatMap { it.hours }
                val previous =
                    scope.ready.filter { it.venueId in compared }.flatMap { venue ->
                        comparedSpans.hours(venue.venueId, ranges, scope, today, now, requireNotNull(venue.dataAsOf))
                    }
                val currentShare = LossCalculator.sharePercent(LossCalculator.loss(current).totalMinor, current.sumOf { it.paidMinor })
                val previousShare = LossCalculator.sharePercent(LossCalculator.loss(previous).totalMinor, previous.sumOf { it.paidMinor })
                LossComparisonView(
                    from = ranges.comparedFrom,
                    to = ranges.comparedTo,
                    upToSameTime = ranges.comparedCutDay != null,
                    venueIds = compared,
                    currentSharePercent = currentShare,
                    previousSharePercent = previousShare,
                    changePoints = LossCalculator.changePoints(currentShare, previousShare),
                )
            }

        val posGaps = scope.ready.map { pos.dataGaps(it.venueId, days.first()..days.last()) }
        return LossesView(
            currency = scope.currency,
            businessDay = today,
            timeZone = scope.zone.id,
            venues = scope.venues,
            ranges = ranges,
            figures = figures,
            comparison = comparison,
            gaps =
                TodayGapsView(
                    quarantinedBills = posGaps.sumOf { it.quarantinedBills },
                    billsWithoutLines = posGaps.sumOf { it.billsWithoutLines },
                    outdatedBills = posGaps.sumOf { it.outdatedBills },
                    paymentMismatchBills = inputs.flatMap { it.hours }.sumOf { it.paymentMismatchBills },
                ),
        )
    }
}
