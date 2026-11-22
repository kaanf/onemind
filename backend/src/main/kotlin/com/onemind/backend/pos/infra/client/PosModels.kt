package com.onemind.backend.pos.infra.client

import java.time.Instant
import java.time.OffsetDateTime

// What a PosProvider returns: only the verified Storyous fields (docs/integrations/STORYOUS.md), with
// their names. Money values are decimal strings in the bill currency ("129.00"), as in the real API;
// they become minor units at the boundary (pos/domain/PosMoney). Raw values (payment method, discount
// type, measure) are passed through unchanged.

data class PosPlaceRef(
    val merchantId: String,
    val placeId: String,
)

enum class PosPlaceState { ACTIVE, DISABLED, HIDDEN }

data class PosAddress(
    val street: String?,
    val city: String?,
    val zip: String?,
    val countryCode: String?,
)

/** A place of a merchant; Storyous gives no time zone and no opening hours. */
data class PosPlace(
    val placeId: String,
    val name: String,
    val state: PosPlaceState,
    val address: PosAddress?,
)

data class PosMerchant(
    val merchantId: String,
    val name: String,
    val businessId: String?,
    val vatId: String?,
    val isVatPayer: Boolean,
    val countryCode: String,
    val currencyCode: String,
    val places: List<PosPlace>,
)

/** Filters of `GET /bills`; [from] and [till] bound `paidAt`, [modifiedSince] bounds `lastModifiedAt`. */
data class PosBillQuery(
    val from: Instant? = null,
    val till: Instant? = null,
    val modifiedSince: Instant? = null,
    val limit: Int = 100,
    val lastBillId: String? = null,
    val includeDeleted: Boolean = false,
)

/** [nextPage] is an opaque cursor for the next page (pass it as `lastBillId`), `null` on the last one. */
data class PosBillPage(
    val bills: List<PosBill>,
    val nextPage: String?,
)

/** A staff member as Storyous puts them on a bill (`createdBy` / `paidBy`); there is no staff endpoint. */
data class PosPerson(
    val personId: String,
    val fullName: String?,
    val userName: String?,
)

data class PosPayment(
    val paymentMethod: String,
    val priceWithVat: String,
)

/** A paid bill. Storyous gives no open bills; [deleted] bills come only with `includeDeleted`. */
data class PosBill(
    val billId: String,
    val sessionCreated: OffsetDateTime?,
    val createdAt: OffsetDateTime?,
    val paidAt: OffsetDateTime,
    val fiscalizedAt: OffsetDateTime?,
    val finalPrice: String,
    val finalPriceWithoutTax: String?,
    val discount: String,
    val rounding: String,
    val tips: String,
    val currencyCode: String,
    val refunded: Boolean,
    val deleted: Boolean,
    val paymentMethod: String?,
    val payments: List<PosPayment>,
    val createdBy: PosPerson?,
    val paidBy: PosPerson?,
    val personCount: Int?,
    val deskId: String?,
    val lastModifiedAt: OffsetDateTime,
)

data class PosItemDiscount(
    val originalPrice: String?,
    val amountType: String?,
    val percentValue: String?,
    val absoluteValue: String?,
    val discountType: String?,
    val note: String?,
)

/** A bill line; [price] is the unit price with VAT, [amount] a decimal string. No per-item time. */
data class PosBillItem(
    val name: String,
    val amount: String,
    val measure: String?,
    val price: String,
    val vatRate: String?,
    val productId: String?,
    val categoryId: String?,
    val ean: String?,
    val discounts: List<PosItemDiscount>,
)

data class PosBillDetail(
    val bill: PosBill,
    val items: List<PosBillItem>,
)

data class PosSection(
    val sectionId: String,
    val name: String,
    val color: String?,
)

/** No seats and no floor position: Storyous doesn't have them. */
data class PosDesk(
    val deskId: String,
    val sectionId: String?,
    val name: String,
    val code: String?,
    val virtual: Boolean,
    val removed: Boolean,
)

data class PosDeskView(
    val sections: List<PosSection>,
    val desks: List<PosDesk>,
)

data class PosCategory(
    val categoryId: String,
    val name: String,
    val parentId: String?,
)

data class PosProduct(
    val productId: String,
    val categoryId: String?,
    val name: String,
    val measure: String?,
    val ean: String?,
    val price: String?,
    val vatRate: String?,
    val enabled: Boolean,
)

data class PosMenu(
    val categories: List<PosCategory>,
    val products: List<PosProduct>,
)
