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
