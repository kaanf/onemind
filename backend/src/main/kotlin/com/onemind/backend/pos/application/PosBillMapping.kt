package com.onemind.backend.pos.application

import com.onemind.backend.pos.domain.BillAttribution
import com.onemind.backend.pos.domain.PaymentKind
import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.domain.SyncedBill
import com.onemind.backend.pos.domain.SyncedBillItem
import com.onemind.backend.pos.domain.SyncedItemDiscount
import com.onemind.backend.pos.domain.SyncedPayment
import com.onemind.backend.pos.domain.SyncedPerson
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
 * STORYOUS.md, "Assumptions to verify".
 */
object PosBillMapping {
    fun toSynced(
        detail: PosBillDetail,
        zone: ZoneId,
        businessDayStart: LocalTime,
    ): SyncedBill {
        val bill = detail.bill
        val currency = Currency.getInstance(bill.currencyCode)
        val paidAt = bill.paidAt.toInstant()
        return SyncedBill(
            externalId = bill.billId,
            sessionStartedAt = bill.sessionCreated?.toInstant(),
            posCreatedAt = bill.createdAt?.toInstant(),
            paidAt = paidAt,
            fiscalizedAt = bill.fiscalizedAt?.toInstant(),
            businessDay = BillAttribution.businessDay(paidAt, zone, businessDayStart),
            totalMinor = PosMoney.minorUnits(bill.finalPrice, currency),
            totalWithoutTaxMinor = bill.finalPriceWithoutTax?.let { PosMoney.minorUnits(it, currency) },
            discountMinor = PosMoney.minorUnits(bill.discount, currency),
            roundingMinor = PosMoney.minorUnits(bill.rounding, currency),
            tipsMinor = PosMoney.minorUnits(bill.tips, currency),
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
                    val unitPriceMinor = PosMoney.minorUnits(item.price, currency)
                    val quantity = BigDecimal(item.amount.trim())
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
                        vatRate = item.vatRate?.trim()?.toBigDecimal(),
                        discountMinor = discountMinor,
                        totalMinor = grossMinor - discountMinor,
                        discounts = discounts,
                    )
                },
            payments =
                bill.payments.map {
                    SyncedPayment(it.paymentMethod, PaymentKind.of(it.paymentMethod), PosMoney.minorUnits(it.priceWithVat, currency))
                },
        )
    }

    private fun PosPerson.toSynced() = SyncedPerson(personId, fullName, userName)

    private fun PosItemDiscount.toSynced(currency: Currency) =
        SyncedItemDiscount(
            discountType = discountType,
            amountType = amountType,
            percentValue = percentValue?.trim()?.toBigDecimal(),
            absoluteMinor = absoluteValue?.let { PosMoney.minorUnits(it, currency) },
            originalPriceMinor = originalPrice?.let { PosMoney.minorUnits(it, currency) },
            note = note,
        )

    /** The absolute amount, else the percentage of the original price; 0 if the POS sent neither. */
    private fun SyncedItemDiscount.amountMinor(): Long =
        absoluteMinor
            ?: percentValue?.let { percent ->
                originalPriceMinor?.let { original ->
                    (BigDecimal.valueOf(original) * percent).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()
                }
            }
            ?: 0
}
