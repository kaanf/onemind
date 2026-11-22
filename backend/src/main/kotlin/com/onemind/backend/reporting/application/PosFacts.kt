package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.BillFact
import com.onemind.backend.pos.api.BillLineFact
import com.onemind.backend.reporting.domain.RollupBill
import com.onemind.backend.reporting.domain.RollupDiscount
import com.onemind.backend.reporting.domain.RollupLine
import com.onemind.backend.reporting.domain.RollupPayment
import java.util.UUID

// POS facts (pos/api/PosReadModel) as the reporting calculations take them.

/** Every page of a keyset-paged read, from the first. */
internal fun <T> allPages(page: (UUID?) -> Pair<List<T>, UUID?>): List<T> {
    val all = mutableListOf<T>()
    var after: UUID? = null
    do {
        val (items, next) = page(after)
        all += items
        after = next
    } while (after != null)
    return all
}

internal fun BillFact.toRollup() =
    RollupBill(
        billId = billId,
        paidAt = paidAt,
        sessionStartedAt = sessionStartedAt,
        totalMinor = totalMinor,
        totalWithoutTaxMinor = totalWithoutTaxMinor,
        discountMinor = discountMinor,
        tipsMinor = tipsMinor,
        refunded = refunded,
        deleted = deleted,
        deskId = deskId,
        createdByStaffId = createdByStaffId,
        paidByStaffId = paidByStaffId,
        guestCount = guestCount,
        payments = payments.map { RollupPayment(it.kind, it.amountMinor) },
    )

internal fun BillLineFact.toRollup() =
    RollupLine(
        billId = billId,
        productId = productId,
        quantity = quantity,
        discountMinor = discountMinor,
        totalMinor = totalMinor,
        discounts = discounts.map { RollupDiscount(it.type, it.amountMinor) },
    )
