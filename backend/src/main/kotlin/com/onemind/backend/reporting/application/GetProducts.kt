package com.onemind.backend.reporting.application

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.MenuCategory
import com.onemind.backend.reporting.domain.ProductName
import com.onemind.backend.reporting.domain.ProductsCalculator
import com.onemind.backend.reporting.domain.ProductsFigures
import com.onemind.backend.reporting.domain.VenueProducts
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

data class ProductsView(
    val currency: Currency,
    /** The day shown. */
    val businessDay: LocalDate,
    /** The venues' current business day (one clock). */
    val today: LocalDate,
    val timeZone: String,
    val venues: List<ScopedVenue>,
    /** The venues with data for [businessDay]: synced, and their data starts on or before it. */
    val coveredVenueIds: Set<UUID>,
    /** Null when no requested venue has data for the day; empty lists on a real day without sales. */
    val figures: ProductsFigures?,
    val gaps: TodayGapsView,
)

/**
 * Screen 02a: products sold on one business day, per hour of bill opening (`docs/domain/METRICS.md`,
 * products and hours). Venues are resolved like Today and Sales ([DashboardScopes]); figures come from
 * the stored product hours, names and categories from the POS menu ([PosReadModel.menu]). Only venues
 * whose data covers the day count (synced, first rollup day on or before it); none: no data, never an
 * empty day.
 */
@Service
class GetProducts(
    private val scopes: DashboardScopes,
    private val rollups: RollupRepository,
    private val pos: PosReadModel,
    private val clock: Clock,
) {
    fun get(
        userId: UUID,
        venueIds: Collection<UUID>,
        date: LocalDate?,
    ): ProductsView {
        val scope = scopes.resolve(userId, venueIds)
        val today = scope.businessDay(clock.instant())
        val day = date ?: today
        if (day.isAfter(today)) throw SalesDateInFutureException()

        // A day before a venue's data starts is no data for it (rule 2), not a day without sales.
        val covered = scope.ready.filter { venue -> rollups.firstDay(venue.venueId)?.let { !it.isAfter(day) } == true }
        val inputs =
            covered.map { venue ->
                val hours = rollups.productHours(venue.venueId, day)
                val menu = pos.menu(venue.venueId, hours.map { it.productId }.distinct())
                VenueProducts(
                    venueId = venue.venueId,
                    hours = hours,
                    names = menu.products.associate { it.productId to ProductName(it.name, it.categoryId) },
                    categories = menu.categories.map { MenuCategory(it.categoryId, it.name, it.parentId) },
                )
            }
        val dayHours = BusinessDayCalendar.localHours(day, scope.zone, scope.businessDayStart)
        val figures = if (inputs.isEmpty()) null else ProductsCalculator.compute(inputs, dayHours)
        val posGaps = covered.map { pos.dataGaps(it.venueId, day..day) }
        return ProductsView(
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
                    billsWithoutLines = posGaps.sumOf { it.billsWithoutLines },
                    outdatedBills = posGaps.sumOf { it.outdatedBills },
                    // Always 0 here: a bill whose payments don't add up still has its lines in product figures.
                    paymentMismatchBills = 0,
                ),
        )
    }
}
