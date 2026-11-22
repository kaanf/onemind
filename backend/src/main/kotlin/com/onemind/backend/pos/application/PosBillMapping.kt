package com.onemind.backend.pos.application

import com.onemind.backend.pos.domain.BillAttribution
import com.onemind.backend.pos.domain.DiscountAmount
import com.onemind.backend.pos.domain.PaymentKind
import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.domain.QuarantineReason
import com.onemind.backend.pos.domain.SyncedBill
import com.onemind.backend.pos.domain.SyncedBillItem
import com.onemind.backend.pos.domain.SyncedItemDiscount
import com.onemind.backend.pos.domain.SyncedPayment
import com.onemind.backend.pos.domain.SyncedPerson
import com.onemind.backend.pos.domain.UnreadableBillException
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosItemDiscount
import com.onemind.backend.pos.infra.client.PosPerson
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

/**
 * A POS bill (decimal strings, POS field names) as the sync stores it (minor units). Only what the POS
 * sends is filled; the derived values (line totals, discount amounts) follow the assumptions listed in
 * STORYOUS.md, "Assumptions to verify". Unit prices finer than the currency are rounded half-up (owner
 * decision 2026-11-22); any other value that can't be stored exactly throws [UnreadableBillException]:
 * the bill is quarantined, never stored with a guessed value.
 */
object PosBillMapping {
    private val log = LoggerFactory.getLogger(PosBillMapping::class.java)

    fun toSynced(
        detail: PosBillDetail,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): SyncedBill {
        val header = header(detail.bill, zone, businessDayStart)
        return header.copy(items = items(detail, header.currency))
    }

    /**
     * Only the bill header as the listing gives it (totals, flags, payments), for a bill whose detail or
     * items can't be read: stored without lines and flagged, so its revenue, refund or deletion counts.
     */
    fun toSyncedHeader(
        bill: PosBill,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): SyncedBill = header(bill, zone, businessDayStart).copy(itemsUnreadable = true)

    private fun header(
        bill: PosBill,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): SyncedBill {
        val currency = currency(bill.currencyCode)
        val paidAt = bill.paidAt.toInstant()
        return SyncedBill(
            externalId = bill.billId,
            sessionStartedAt = bill.sessionCreated?.toInstant(),
            posCreatedAt = bill.createdAt?.toInstant(),
            paidAt = paidAt,
            fiscalizedAt = bill.fiscalizedAt?.toInstant(),
            businessDay = BillAttribution.businessDay(paidAt, zone, businessDayStart),
            totalMinor = money(bill.finalPrice, currency),
            totalWithoutTaxMinor = bill.finalPriceWithoutTax?.let { money(it, currency) },
            discountMinor = money(bill.discount, currency),
            roundingMinor = money(bill.rounding, currency),
            tipsMinor = money(bill.tips, currency),
            currency = currency,
            refunded = bill.refunded,
            deleted = bill.deleted,
            deskExternalId = bill.deskId,
            createdBy = bill.createdBy?.toSynced(),
            paidBy = bill.paidBy?.toSynced(),
            guestCount = bill.personCount,
            externalModifiedAt = bill.lastModifiedAt.toInstant(),
            items = emptyList(),
            payments =
                bill.payments.map {
                    SyncedPayment(it.paymentMethod, PaymentKind.of(it.paymentMethod), money(it.priceWithVat, currency))
                },
        )
    }

    private fun items(
        detail: PosBillDetail,
        currency: Currency,
    ): List<SyncedBillItem> =
        detail.items.mapIndexed { index, item ->
            val unitPriceMinor = price(detail.bill.billId, item.price, currency)
            val quantity = decimal(item.amount, QuarantineReason.BAD_AMOUNT)
            val discounts = item.discounts.map { it.toSynced(currency) }
            val discountMinor = discounts.sumOf { it.amountMinor() }
            val grossMinor = (BigDecimal.valueOf(unitPriceMinor) * quantity).setScale(0, RoundingMode.HALF_UP).longValueExact()
            SyncedBillItem(
                lineNo = index + 1,
                productExternalId = item.productId,
                name = item.name,
                quantity = quantity,
                measure = item.measure,
                unitPriceMinor = unitPriceMinor,
                vatRate = item.vatRate?.let { decimal(it, QuarantineReason.BAD_VAT_RATE) },
                discountMinor = discountMinor,
                totalMinor = grossMinor - discountMinor,
                discounts = discounts,
            )
        }

    private fun currency(code: String): Currency =
        try {
            Currency.getInstance(code.trim())
        } catch (e: IllegalArgumentException) {
            throw UnreadableBillException(QuarantineReason.BAD_CURRENCY)
        }

    private fun decimal(
        value: String,
        reason: QuarantineReason,
    ): BigDecimal = value.trim().toBigDecimalOrNull() ?: throw UnreadableBillException(reason)

    /** Exact minor units: not a number → [QuarantineReason.BAD_AMOUNT], too many digits → sub-minor. */
    private fun money(
        value: String,
        currency: Currency,
    ): Long {
        decimal(value, QuarantineReason.BAD_AMOUNT)
        return try {
            PosMoney.minorUnits(value, currency)
        } catch (e: IllegalArgumentException) {
            throw UnreadableBillException(QuarantineReason.SUB_MINOR_AMOUNT)
        }
    }

    /** A line's unit price: rounded half-up to minor units when finer (owner decision 2026-11-22). */
    private fun price(
        billId: String,
        value: String,
        currency: Currency,
    ): Long {
        decimal(value, QuarantineReason.BAD_AMOUNT)
        if (PosMoney.needsRounding(value, currency)) log.debug("POS line price rounded: billId={}", billId)
        return PosMoney.minorUnitsRounded(value, currency)
    }

    private fun PosPerson.toSynced() = SyncedPerson(personId, fullName, userName)

    private fun PosItemDiscount.toSynced(currency: Currency) =
        SyncedItemDiscount(
            discountType = discountType,
            amountType = amountType,
            percentValue = percentValue?.let { decimal(it, QuarantineReason.BAD_AMOUNT) },
            absoluteMinor = absoluteValue?.let { money(it, currency) },
            // The pre-discount price is a price: rounded like the unit price; the discount amount stays exact.
            originalPriceMinor =
                originalPrice?.let {
                    decimal(it, QuarantineReason.BAD_AMOUNT)
                    PosMoney.minorUnitsRounded(it, currency)
                },
            note = note,
        )

    /** The absolute amount, else the percentage of the original price; neither → the bill can't be stored. */
    private fun SyncedItemDiscount.amountMinor(): Long =
        DiscountAmount.of(absoluteMinor, percentValue, originalPriceMinor)
            ?: throw UnreadableBillException(QuarantineReason.DISCOUNT_AMOUNT_MISSING)
}
