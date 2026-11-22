package com.onemind.backend.pos

import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import com.onemind.backend.testing.MutableClock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The demo POS: deterministic, realistic and limited to fields the real Storyous API has. */
class MockPosProviderTests {
    private val now = Instant.parse("2026-11-20T21:00:00Z")
    private val clock = MutableClock(now)
    private val provider = MockPosProvider(clock)
    private val letna = PosPlaceRef("demo-merchant", "demo-letna")
    private val czk = Currency.getInstance("CZK")

    private fun all(
        query: PosBillQuery,
        on: MockPosProvider = provider,
        place: PosPlaceRef = letna,
    ): List<PosBill> {
        val bills = mutableListOf<PosBill>()
        var cursor: String? = query.lastBillId
        do {
            val page = on.bills(place, query.copy(lastBillId = cursor))
            bills += page.bills
            cursor = page.nextPage
        } while (cursor != null)
        return bills
    }

    private val lastMonth = PosBillQuery(from = now - Duration.ofDays(30), includeDeleted = true)

    @Test
    fun `the demo merchant has demo-named places, a disabled one without bills`() {
        val merchant = assertNotNull(provider.merchant("demo-merchant"))
        assertTrue(merchant.places.all { it.name.startsWith("Demo") })
        assertEquals("CZK", merchant.currencyCode)
        assertNull(provider.merchant("someone-else"))
        assertTrue(provider.bills(PosPlaceRef("demo-merchant", "demo-oldtown"), lastMonth).bills.isEmpty())
        assertTrue(provider.menu(letna).products.isNotEmpty())
        assertTrue(provider.deskView(letna).desks.any { it.virtual })
    }

    @Test
    fun `the same window gives the same bills, whatever was asked before`() {
        val other = MockPosProvider(MutableClock(now))
        other.bills(letna, PosBillQuery(from = now - Duration.ofDays(3)))
        assertEquals(all(lastMonth), all(lastMonth, on = other))
        val bill = all(lastMonth).first()
        assertEquals(provider.billDetail(letna, bill.billId), other.billDetail(letna, bill.billId))
    }

    @Test
    fun `no bill is paid after now, and later bills appear as time passes`() {
        val before = all(lastMonth)
        assertTrue(before.all { !it.paidAt.toInstant().isAfter(now) })
        clock.advance(Duration.ofHours(2))
        assertTrue(all(lastMonth).size > before.size)
    }

    @Test
    fun `bills cross midnight and every night has many`() {
        val bills = all(lastMonth)
        assertTrue(bills.size > 30 * 40)
        assertTrue(bills.any { it.sessionCreated!!.toLocalDate() != it.paidAt.toLocalDate() })
        assertTrue(bills.all { !it.sessionCreated!!.isAfter(it.paidAt) })
    }

    @Test
    fun `the autumn DST night has a 02_30 bill per offset, the spring night moves it to 03_30`() {
        val autumn1 = assertNotNull(provider.billDetail(letna, "LT20261024900")).bill.paidAt
        val autumn2 = assertNotNull(provider.billDetail(letna, "LT20261024901")).bill.paidAt
        assertEquals(LocalTime.of(2, 30), autumn1.toLocalTime())
        assertEquals(LocalTime.of(2, 30), autumn2.toLocalTime())
        assertEquals(setOf(ZoneOffset.ofHours(2), ZoneOffset.ofHours(1)), setOf(autumn1.offset, autumn2.offset))
        assertEquals(Duration.ofHours(1), Duration.between(autumn1, autumn2).abs())

        val inApril = MockPosProvider(MutableClock(Instant.parse("2026-04-15T12:00:00Z")))
        val spring = assertNotNull(inApril.billDetail(letna, "LT20260328900")).bill.paidAt
        assertEquals(LocalTime.of(3, 30), spring.toLocalTime())
        assertNull(inApril.billDetail(letna, "LT20260328901"), "no second offset in spring")
    }

    @Test
    fun `totals add up from items, discounts, tips and payments`() {
        all(lastMonth).take(200).forEach { bill ->
            val detail = assertNotNull(provider.billDetail(letna, bill.billId))
            val itemsMinor =
                detail.items.sumOf { item ->
                    val gross = PosMoney.minorUnits(item.price, czk) * item.amount.toLong()
                    val discounts = item.discounts.sumOf { discountOf(it.percentValue, it.absoluteValue, gross) }
                    gross - discounts
                }
            val total = PosMoney.minorUnits(bill.finalPrice, czk)
            assertEquals(itemsMinor, total, bill.billId)
            val paid = bill.payments.sumOf { PosMoney.minorUnits(it.priceWithVat, czk) }
            assertEquals(total + PosMoney.minorUnits(bill.tips, czk), paid, bill.billId)
            assertEquals("CZK", bill.currencyCode)
        }
    }

    private fun discountOf(
        percent: String?,
        absolute: String?,
        gross: Long,
    ): Long = percent?.let { gross * it.toLong() / 100 } ?: PosMoney.minorUnits(absolute!!, czk)

    @Test
    fun `discounts, refunds and whole deleted bills occur, deleted ones only on request`() {
        val bills = all(lastMonth)
        assertTrue(bills.any { it.refunded })
        assertTrue(bills.any { it.deleted })
        assertTrue(all(lastMonth.copy(includeDeleted = false)).none { it.deleted })
        val types =
            bills
                .take(
                    400,
                ).flatMap { provider.billDetail(letna, it.billId)!!.items }
                .flatMap { it.discounts }
                .map { it.discountType }
        assertTrue("HAPPY_HOUR" in types)
        assertTrue(bills.any { it.createdBy != it.paidBy })
    }

    @Test
    fun `a refund shows only after it happened, and modifiedSince then returns the bill again`() {
        val refunded = all(lastMonth).first { it.refunded }
        assertTrue(refunded.lastModifiedAt.isAfter(refunded.paidAt))

        val atPayment = MockPosProvider(MutableClock(refunded.paidAt.toInstant() + Duration.ofMinutes(1)))
        val early = assertNotNull(atPayment.billDetail(letna, refunded.billId)).bill
        assertFalse(early.refunded)
        assertEquals(early.paidAt, early.lastModifiedAt)

        val changed = all(PosBillQuery(modifiedSince = refunded.paidAt.toInstant() + Duration.ofMinutes(1)))
        assertTrue(changed.any { it.billId == refunded.billId })
        assertTrue(changed.zipWithNext().all { (a, b) -> !a.lastModifiedAt.isAfter(b.lastModifiedAt) })
    }

    @Test
    fun `pages of any size add up to the same ordered bills`() {
        val window = PosBillQuery(from = now - Duration.ofDays(5), till = now - Duration.ofDays(1))
        val big = provider.bills(letna, window.copy(limit = 10_000))
        assertNull(big.nextPage)
        assertEquals(big.bills, all(window.copy(limit = 37)))
        assertTrue(big.bills.all { !it.paidAt.toInstant().isBefore(window.from) && it.paidAt.toInstant().isBefore(window.till) })
        assertTrue(big.bills.zipWithNext().all { (a, b) -> !a.paidAt.isAfter(b.paidAt) })
    }

    @Test
    fun `the cursor survives bills that change between pages`() {
        val window = PosBillQuery(modifiedSince = now - Duration.ofDays(3), limit = 50)
        val first = provider.bills(letna, window)
        val cursor = assertNotNull(first.nextPage)

        // Time passes: new bills are paid, bills of the first page may be refunded or deleted.
        clock.advance(Duration.ofDays(1))
        val rest = all(window.copy(lastBillId = cursor))

        val lastSeen = first.bills.last()
        assertTrue(rest.isNotEmpty())
        // The last bill seen comes again only if it changed since (refunded or deleted later).
        assertTrue(rest.filter { it.billId == lastSeen.billId }.all { it.lastModifiedAt.isAfter(lastSeen.lastModifiedAt) })
        assertTrue(
            rest.all { compareValuesBy(it, lastSeen, { b -> b.lastModifiedAt.toInstant() }, { b -> b.billId }) > 0 },
            "every bill after the cursor",
        )
        assertEquals(rest.size, rest.map { it.billId }.toSet().size)
    }

    @Test
    fun `a cursor that isn't one fails, a bill beyond the history is unknown`() {
        assertTrue(runCatching { provider.bills(letna, lastMonth.copy(lastBillId = "LT20261101001")) }.isFailure)
        assertNull(provider.billDetail(letna, "LT20250101900"))
    }
}
