package com.onemind.backend.pos.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Bills as the sync stored them, read back for other modules (pos/api/PosReadModel maps them to its DTOs).

data class StoredPayment(
    val kind: String,
    val amountMinor: Long,
)

data class StoredBill(
    val billId: UUID,
    val businessDay: LocalDate,
    val paidAt: Instant,
    val sessionStartedAt: Instant?,
    val totalMinor: Long,
    val totalWithoutTaxMinor: Long?,
    val discountMinor: Long,
    val tipsMinor: Long,
    val refunded: Boolean,
    val deleted: Boolean,
    val deskId: UUID?,
    val createdByStaffId: UUID?,
    val paidByStaffId: UUID?,
    val guestCount: Int?,
    val externalModifiedAt: Instant,
    val itemsUnreadable: Boolean,
    val payments: List<StoredPayment>,
)

data class StoredDiscount(
    val discountType: String?,
    val absoluteMinor: Long?,
    val percentValue: BigDecimal?,
    val originalPriceMinor: Long?,
)

data class StoredBillLine(
    val lineId: UUID,
    val billId: UUID,
    val productId: UUID?,
    val quantity: BigDecimal,
    val discountMinor: Long,
    val totalMinor: Long,
    val discounts: List<StoredDiscount>,
)

/** A product of the venue's POS menu as stored; kept after it leaves the menu, so old sales keep their name. */
data class StoredProduct(
    val productId: UUID,
    val name: String,
    val categoryId: UUID?,
)

/** A staff member of the venue's POS account as stored from bills; the POS may send neither name. */
data class StoredStaff(
    val staffId: UUID,
    val fullName: String?,
    val userName: String?,
)

/** A category of the venue's POS menu as stored. */
data class StoredCategory(
    val categoryId: UUID,
    val name: String,
    val parentId: UUID?,
)
