package com.onemind.backend.pos

import com.onemind.backend.pos.application.PosBillMapping
import com.onemind.backend.pos.domain.PaymentKind
import com.onemind.backend.pos.domain.QuarantineReason
import com.onemind.backend.pos.domain.UnreadableBillException
import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.testing.MutableClock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PosBillMappingTest {
    private val mock = MockPosProvider(MutableClock(Instant.parse("2026-11-20T21:00:00Z")))
    private val letna = PosPlaceRef("demo-merchant", "demo-letna")
    private val detail: PosBillDetail = requireNotNull(mock.billDetail(letna, "LT20261119001"))
    private val zone = ZoneId.of("Europe/Prague")
    private val cutOff = LocalTime.of(5, 0)

    private fun reasonOf(bad: PosBillDetail): QuarantineReason =
        assertFailsWith<UnreadableBillException> { PosBillMapping.toSynced(bad, zone, cutOff) }.reason

    @Test
    fun `a readable bill maps to exact minor units`() {
        val bill = PosBillMapping.toSynced(detail, zone, cutOff)
        assertEquals(
            detail.bill.finalPrice
                .replace(".", "")
                .toLong(),
            bill.totalMinor,
        )
        assertEquals(bill.totalMinor, bill.items.sumOf { it.totalMinor })
    }

    @Test
    fun `values that can't be stored exactly name their reason`() {
        assertEquals(QuarantineReason.BAD_CURRENCY, reasonOf(detail.copy(bill = detail.bill.copy(currencyCode = "XXQ"))))
        assertEquals(QuarantineReason.BAD_AMOUNT, reasonOf(detail.copy(bill = detail.bill.copy(tips = "ten"))))
        assertEquals(QuarantineReason.SUB_MINOR_AMOUNT, reasonOf(detail.copy(bill = detail.bill.copy(finalPrice = "1.005"))))
        assertEquals(QuarantineReason.BAD_AMOUNT, reasonOf(detail.copy(items = detail.items.map { it.copy(amount = "one") })))
        assertEquals(QuarantineReason.BAD_VAT_RATE, reasonOf(detail.copy(items = detail.items.map { it.copy(vatRate = "21%") })))
    }

    @Test
    fun `raw payment methods in English and Czech`() {
        assertEquals(PaymentKind.CARD, PaymentKind.of("card"))
        assertEquals(PaymentKind.CARD, PaymentKind.of("Karta"))
        assertEquals(PaymentKind.CASH, PaymentKind.of(" hotovost "))
        assertEquals(PaymentKind.CASH, PaymentKind.of("CASH"))
        assertEquals(PaymentKind.OTHER, PaymentKind.of("voucher"))
    }

    @Test
    fun `a sub-minor line price is rounded half-up, owner decision 2026-11-22`() {
        val first = detail.items.first()
        val rounded =
            detail.copy(
                items = listOf(first.copy(price = "68.995", amount = "1", discounts = emptyList())) + detail.items.drop(1),
            )
        assertEquals(
            6900,
            PosBillMapping
                .toSynced(rounded, zone, cutOff)
                .items
                .first()
                .unitPriceMinor,
        )
    }
}
