package com.onemind.backend.pos.infra.client

/**
 * The POS behind one connection, shaped like the verified Storyous read endpoints
 * (`docs/integrations/STORYOUS.md`). **Read-only** (product rule 1): every method maps to a GET, and
 * nothing here may create, change or delete anything in the POS. Implementations: [mock.MockPosProvider]
 * (dev/demo only) now, the Storyous client later (P15); ADR 0013 R9 exception. A POS that can't be read
 * is a [PosProviderException]; anything else thrown is a bug.
 */

interface PosProvider {
    /** `GET /merchants/{merchantId}`: the merchant and its places, or `null` if it isn't assigned to us. */
    fun merchant(merchantId: String): PosMerchant?

    /**
     * `GET /bills/{merchantId}-{placeId}`: one page of **paid** bills, ordered by `paidAt`, or by
     * `lastModifiedAt` when [PosBillQuery.modifiedSince] is set. Pass [PosBillPage.nextPage] as
     * [PosBillQuery.lastBillId] for the next page.
     */
    fun bills(
        place: PosPlaceRef,
        query: PosBillQuery,
    ): PosBillPage

    /** `GET /bills/{merchantId}-{placeId}/{billId}`: the bill with its items, or `null` if unknown. */
    fun billDetail(
        place: PosPlaceRef,
        billId: String,
    ): PosBillDetail?

    /** `GET /deskViews/{merchantId}-{placeId}`: sections and desks. */
    fun deskView(place: PosPlaceRef): PosDeskView

    /** `GET /menu/...`: categories and products of the place. */
    fun menu(place: PosPlaceRef): PosMenu
}

/** A [PosProvider] couldn't read the POS (network, auth, an answer it can't parse); the caller may retry. */
class PosProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
