package com.onemind.backend.account.application

import com.onemind.backend.account.infra.persistence.VenueRegistryRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.UUID

/** Where a venue's business day starts and ends: its zone and cut-off (owner settings, R28). */
data class VenueBusinessDaySettings(
    val zone: ZoneId,
    val businessDayStart: LocalTime,
)

/**
 * Venues as the POS sync discovers them (DB_SCHEMA `venue`: "created when the POS sync discovers a
 * place"), reached by `pos` through [VenueRegistry][com.onemind.backend.account.api.VenueRegistry].
 * Runs inside the caller's transaction. Time zone and cut-off keep the schema defaults until the owner
 * changes them; the POS has neither.
 */
@Service
class RegisterPosVenues(
    private val venues: VenueRegistryRepository,
    private val clock: Clock,
) {
    fun register(
        organizationId: UUID,
        name: String,
        currency: Currency,
        addressLine: String?,
        city: String?,
    ): UUID = venues.insert(organizationId, name, currency, addressLine, city)

    /** Takes over the POS name and address; `false` if the venue doesn't exist. The status stays as it is. */
    fun update(
        venueId: UUID,
        name: String,
        addressLine: String?,
        city: String?,
    ): Boolean = venues.update(venueId, name, addressLine, city, clock.instant())

    fun businessDay(venueId: UUID): VenueBusinessDaySettings? =
        venues.businessDay(venueId)?.let { VenueBusinessDaySettings(ZoneId.of(it.timeZone), it.businessDayStart) }
}
