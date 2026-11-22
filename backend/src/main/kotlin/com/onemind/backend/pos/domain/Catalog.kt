package com.onemind.backend.pos.domain

import java.math.BigDecimal

// The POS catalog of a venue as the sync stores it, keyed by the POS's own IDs. Values the POS doesn't
// send stay null (rule 2).

data class CatalogCategory(
    val externalId: String,
    val name: String,
    val parentExternalId: String?,
)

data class CatalogProduct(
    val externalId: String,
    val categoryExternalId: String?,
    val name: String,
    val measure: String?,
    val ean: String?,
    val vatRate: BigDecimal?,
    val priceMinor: Long?,
    val active: Boolean,
)

data class CatalogSection(
    val externalId: String,
    val name: String,
    val color: String?,
)

data class CatalogDesk(
    val externalId: String,
    val sectionExternalId: String?,
    val name: String,
    val code: String?,
    val virtual: Boolean,
    val removed: Boolean,
)
