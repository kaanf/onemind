package com.onemind.backend.pos.application

import com.onemind.backend.account.api.PosVenueDetails
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.pos.infra.client.PosPlace
import com.onemind.backend.pos.infra.client.PosPlaceState
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.PosConnectionRow
import com.onemind.backend.pos.infra.persistence.VenueLinkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.Currency
import java.util.UUID

/** A venue linked to a place of the connection's merchant. */
data class LinkedVenue(
    val venueId: UUID,
    val placeId: String,
    val active: Boolean,
)

/** The merchant's currency and its venues after [SyncPlaces]. */
data class SyncedPlaces(
    val currency: Currency,
    val venues: List<LinkedVenue>,
)

class PosMerchantNotFoundException : RuntimeException("The POS doesn't know the connection's merchant")

/**
 * Places → venues: a place the POS reports for the first time becomes a venue of the connection's
 * organization (through `account`'s [VenueRegistry]) and a `venue_link`; known places take over the
 * POS name, address and state (`DISABLED` / `HIDDEN` → disabled venue). The POS call runs before the
 * transaction.
 */
@Service
class SyncPlaces(
    private val venueRegistry: VenueRegistry,
    private val venueLinks: VenueLinkRepository,
    private val transactions: TransactionTemplate,
) {
    fun run(
        connection: PosConnectionRow,
        provider: PosProvider,
    ): SyncedPlaces {
        val merchant = provider.merchant(connection.externalMerchantId) ?: throw PosMerchantNotFoundException()
        val currency = Currency.getInstance(merchant.currencyCode)
        val venues =
            requireNotNull(
                transactions.execute {
                    val known = venueLinks.linked(connection.id).associate { it.externalPlaceId to it.venueId }
                    merchant.places.map { place ->
                        val active = place.state == PosPlaceState.ACTIVE
                        val venueId =
                            known[place.placeId]?.also { venueRegistry.update(it, place.details(), active) }
                                ?: venueRegistry.register(connection.organizationId, place.details(), currency).also {
                                    venueLinks.insert(it, connection.id, place.placeId)
                                    if (!active) venueRegistry.update(it, place.details(), active = false)
                                }
                        LinkedVenue(venueId, place.placeId, active)
                    }
                },
            )
        return SyncedPlaces(currency, venues)
    }

    private fun PosPlace.details() = PosVenueDetails(name, address?.street, address?.city)
}
