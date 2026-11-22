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
 * Places → venues: a place the POS reports active for the first time becomes a venue of the connection's
 * organization (through `account`'s [VenueRegistry]) and a `venue_link`; known places take over the POS
 * name and address. A place reported disabled/hidden, or no longer reported, only deactivates its link:
 * the sync skips it, the venue and its history stay (pending owner confirmation, STORYOUS.md); reported
 * active again, the link is active again. The POS call runs before the transaction.
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
                    venueLinks.lockConnection(connection.id)
                    val links = venueLinks.linked(connection.id).associateBy { it.externalPlaceId }
                    val reported =
                        merchant.places.mapNotNull { place ->
                            val active = place.state == PosPlaceState.ACTIVE
                            val link = links[place.placeId]
                            when {
                                link != null -> {
                                    venueRegistry.update(link.venueId, place.details())
                                    venueLinks.setActive(link.venueId, active)
                                    LinkedVenue(link.venueId, place.placeId, active)
                                }

                                // A place first seen inactive gets no venue until it is active.
                                !active -> {
                                    null
                                }

                                else -> {
                                    val venueId = venueRegistry.register(connection.organizationId, place.details(), currency)
                                    venueLinks.insert(venueId, connection.id, place.placeId)
                                    LinkedVenue(venueId, place.placeId, active = true)
                                }
                            }
                        }
                    val gone = links.values.filter { link -> merchant.places.none { it.placeId == link.externalPlaceId } }
                    gone.forEach { venueLinks.setActive(it.venueId, false) }
                    reported + gone.map { LinkedVenue(it.venueId, it.externalPlaceId, active = false) }
                },
            )
        return SyncedPlaces(currency, venues)
    }

    private fun PosPlace.details() = PosVenueDetails(name, address?.street, address?.city)
}
