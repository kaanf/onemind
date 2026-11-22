package com.onemind.backend.pos.application

import com.onemind.backend.pos.domain.BillAttribution
import com.onemind.backend.pos.domain.PaymentKind
import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.domain.QuarantineReason
import com.onemind.backend.pos.domain.SyncedBill
import com.onemind.backend.pos.domain.SyncedBillItem
import com.onemind.backend.pos.domain.SyncedItemDiscount
import com.onemind.backend.pos.domain.SyncedPayment
import com.onemind.backend.pos.domain.SyncedPerson
import com.onemind.backend.pos.domain.UnreadableBillException
import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosItemDiscount
import com.onemind.backend.pos.infra.client.PosPerson
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency

/**
 * A POS bill (decimal strings, POS field names) as the sync stores it (minor units). Only what the POS
 * sends is filled; the derived values (line totals, discount amounts) follow the assumptions listed in
 * STORYOUS.md, "Assumptions to verify". A value that can't be stored exactly throws
 * [UnreadableBillException]: the bill is quarantined, never stored with a guessed value.
 */
object PosBillMapping {
    fun toSynced(
        detail: PosBillDetail,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): SyncedBill {
        val bill = detail.bill
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
            items =
                detail.items.mapIndexed { index, item ->
                    val unitPriceMinor = money(item.price, currency)
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
                },
            payments =
                bill.payments.map {
                    SyncedPayment(it.paymentMethod, PaymentKind.of(it.paymentMethod), money(it.priceWithVat, currency))
                },
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
            throw UnreadableBillException(QuarantineReason.SUB_MINOR_PRICE)
        }
    }

    private fun PosPerson.toSynced() = SyncedPerson(personId, fullName, userName)

    private fun PosItemDiscount.toSynced(currency: Currency) =
        SyncedItemDiscount(
            discountType = discountType,
            amountType = amountType,
            percentValue = percentValue?.let { decimal(it, QuarantineReason.BAD_AMOUNT) },
            absoluteMinor = absoluteValue?.let { money(it, currency) },
            originalPriceMinor = originalPrice?.let { money(it, currency) },
            note = note,
        )

    /** The absolute amount, else the percentage of the original price; neither → the bill can't be stored. */
    private fun SyncedItemDiscount.amountMinor(): Long {
        absoluteMinor?.let { return it }
        val percent = percentValue
        val original = originalPriceMinor
        if (percent == null || original == null) throw UnreadableBillException(QuarantineReason.DISCOUNT_AMOUNT_MISSING)
        return (BigDecimal.valueOf(original) * percent).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()
    }
}
