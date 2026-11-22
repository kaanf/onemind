package com.onemind.backend.account.api

import com.onemind.backend.account.application.RegisterPosVenues
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID

/** What the POS reports about a place, as the venue takes it over. */
data class PosVenueDetails(
    val name: String,
    val addressLine: String?,
    val city: String?,
)

/** A venue's business-day settings (AGENTS.md rule 6): its time zone and cut-off. */
data class VenueBusinessDay(
    val zone: ZoneId,
    val businessDayStart: LocalTime,
)

/**
 * The only way `pos` creates and updates venues (ADR 0013 §4: pos "registers the venues it discovers");
 * pos never writes account tables. Facade over [RegisterPosVenues]; calls join the caller's transaction.
 */
@Component
class VenueRegistry(
    private val registerPosVenues: RegisterPosVenues,
) {
    /** A new active venue of [organizationId] for a place the POS reported. */
    fun register(
        organizationId: UUID,
        details: PosVenueDetails,
        currency: Currency,
    ): UUID = registerPosVenues.register(organizationId, details.name, currency, details.addressLine, details.city)

    /** Takes over the place's name and address; `false` if the venue doesn't exist (any more). */
    fun update(
        venueId: UUID,
        details: PosVenueDetails,
        active: Boolean,
    ): Boolean = registerPosVenues.update(venueId, details.name, details.addressLine, details.city, active)

    /** The venue's business-day settings, or `null` if it doesn't exist. */
    fun businessDay(venueId: UUID): VenueBusinessDay? =
        registerPosVenues.businessDay(venueId)?.let { VenueBusinessDay(it.zone, it.businessDayStart) }
}
