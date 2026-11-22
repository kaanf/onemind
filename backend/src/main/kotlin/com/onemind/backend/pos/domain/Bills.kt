package com.onemind.backend.pos.domain

import com.onemind.backend.common.time.BusinessDayCalendar
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

// A paid bill as the sync stores it (DB_SCHEMA `pos.bill`), in minor units of the bill currency. Values
// the POS doesn't send stay null (rule 2); POS references (desk, product, staff) are the POS's own IDs.

data class SyncedPerson(
    val externalId: String,
    val fullName: String?,
    val userName: String?,
)

data class SyncedItemDiscount(
    val discountType: String?,
    val amountType: String?,
    val percentValue: BigDecimal?,
    val absoluteMinor: Long?,
    val originalPriceMinor: Long?,
    val note: String?,
)

data class SyncedBillItem(
    val lineNo: Int,
    val productExternalId: String?,
    val name: String,
    val quantity: BigDecimal,
    val measure: String?,
    val unitPriceMinor: Long,
    val vatRate: BigDecimal?,
    val discountMinor: Long,
    val totalMinor: Long,
    val discounts: List<SyncedItemDiscount>,
)

data class SyncedPayment(
    val methodRaw: String,
    val methodKind: PaymentKind,
    val amountMinor: Long,
)

data class SyncedBill(
    val externalId: String,
    val sessionStartedAt: Instant?,
    val posCreatedAt: Instant?,
    val paidAt: Instant,
    val fiscalizedAt: Instant?,
    val businessDay: LocalDate,
    val totalMinor: Long,
    val totalWithoutTaxMinor: Long?,
    val discountMinor: Long,
    val roundingMinor: Long,
    val tipsMinor: Long,
    val currency: Currency,
    val refunded: Boolean,
    val deleted: Boolean,
    val deskExternalId: String?,
    val createdBy: SyncedPerson?,
    val paidBy: SyncedPerson?,
    val guestCount: Int?,
    val externalModifiedAt: Instant,
    val items: List<SyncedBillItem>,
    val payments: List<SyncedPayment>,
)

/** `payment.method_kind`; the raw POS value is kept next to it. */
enum class PaymentKind {
    CARD,
    CASH,
    OTHER,
    ;

    companion object {
        /** From a raw POS payment method; the Storyous values are to be verified (STORYOUS.md, assumptions). */
        fun of(raw: String): PaymentKind {
            val value = raw.trim().lowercase()
            return when {
                "card" in value -> CARD
                "cash" in value -> CASH
                else -> OTHER
            }
        }
    }
}

/**
 * Which business day a bill belongs to. **Open question M1** (`docs/OPEN_QUESTIONS.md`): by close time
 * or by open time. The documented default is the payment time (DB_SCHEMA `pos.bill.business_day`); this
 * is the only place that decides it, so answering M1 changes this function (and a rebuild of the stored
 * days), nothing else.
 */
object BillAttribution {
    fun businessDay(
        paidAt: Instant,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): LocalDate = BusinessDayCalendar.businessDate(paidAt, zone, businessDayStart)
}
