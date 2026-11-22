package com.onemind.backend.reporting.application

import com.onemind.backend.account.api.VenueAccess
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.common.error.RuleViolationException
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.PosReadModel
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

/** One requested venue and whether it has data yet (rule 2): `dataAsOf` is its last bills sync. */
data class ScopedVenue(
    val venueId: UUID,
    val dataAsOf: Instant?,
) {
    val ready: Boolean get() = dataAsOf != null
}

/** The venues of one dashboard request: one currency, one time zone and cut-off (one clock). */
data class DashboardScope(
    val currency: Currency,
    val zone: ZoneId,
    val businessDayStart: LocalTime,
    val venues: List<ScopedVenue>,
) {
    val ready: List<ScopedVenue> get() = venues.filter { it.ready }

    /** The business day [at] belongs to, for every venue of the scope. */
    fun businessDay(at: Instant): LocalDate = BusinessDayCalendar.businessDate(at, zone, businessDayStart)
}

/**
 * Resolves the venues a dashboard request names (rule 7): every venue is access-checked first. Amounts of
 * different currencies are never added up (`422 CURRENCIES_DIFFER`); venues on different zones or
 * cut-offs are `422 BUSINESS_DAYS_DIFFER` (one chart, one clock; a first-release limit, rule 6).
 */
@Component
class DashboardScopes(
    private val venueAccess: VenueAccess,
    private val venueRegistry: VenueRegistry,
    private val pos: PosReadModel,
) {
    fun resolve(
        userId: UUID,
        venueIds: Collection<UUID>,
    ): DashboardScope {
        val ids = venueIds.distinct()
        require(ids.isNotEmpty()) { "at least one venue (the API validates it)" }
        ids.forEach { venueAccess.requireAccess(userId, it) }
        val settings = ids.associateWith { requireNotNull(venueRegistry.businessDay(it)) { "an accessible venue has settings" } }
        val currency =
            settings.values
                .map { it.currency }
                .distinct()
                .singleOrNull() ?: throw CurrenciesDifferException()
        val clock =
            settings.values
                .map { it.zone to it.businessDayStart }
                .distinct()
                .singleOrNull() ?: throw BusinessDaysDifferException()
        return DashboardScope(
            currency = currency,
            zone = clock.first,
            businessDayStart = clock.second,
            venues = ids.map { ScopedVenue(it, pos.lastBillsSyncAt(it)) },
        )
    }
}
