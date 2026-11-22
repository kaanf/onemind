package com.onemind.backend.reporting

import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.reporting.domain.RollupBill
import com.onemind.backend.reporting.domain.RollupCalculator
import com.onemind.backend.reporting.domain.RollupDiscount
import com.onemind.backend.reporting.domain.RollupLine
import com.onemind.backend.reporting.domain.RollupPayment
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** One definition per test (`docs/domain/METRICS.md`), on Europe/Prague days with the 05:00 cut-off. */
class RollupCalculatorTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val cutOff = LocalTime.of(5, 0)
    private val staffA = UUID.fromString("00000000-0000-7000-8000-00000000000a")
    private val staffB = UUID.fromString("00000000-0000-7000-8000-00000000000b")
    private val desk = UUID.fromString("00000000-0000-7000-8000-0000000000d1")
    private val product = UUID.fromString("00000000-0000-7000-8000-0000000000e1")

    private fun day(date: String) = BusinessDayCalendar.range(LocalDate.parse(date), zone, cutOff)

    private fun bill(
        paidAt: String,
        opened: String? = null,
        total: Long = 10_000,
        tips: Long = 0,
        payments: List<RollupPayment> = listOf(RollupPayment("CARD", total + tips)),
        refunded: Boolean = false,
        deleted: Boolean = false,
        guests: Int? = 2,
        createdBy: UUID? = staffA,
        paidBy: UUID? = staffA,
        deskId: UUID? = desk,
        net: Long? = total * 100 / 121,
    ) = RollupBill(
        billId = UUID.randomUUID(),
        paidAt = Instant.parse(paidAt),
        sessionStartedAt = opened?.let(Instant::parse),
        totalMinor = total,
        totalWithoutTaxMinor = net,
        discountMinor = 0,
        tipsMinor = tips,
        refunded = refunded,
        deleted = deleted,
        deskId = deskId,
        createdByStaffId = createdBy,
        paidByStaffId = paidBy,
        guestCount = guests,
        payments = payments,
    )

    @Test
    fun `revenue is in the local hour of payment, a bill paid after midnight stays in its business day`() {
        // 2026-11-20 is CET (+01:00): 21:30Z = 22:30 local, 00:30Z (next date) = 01:30 local.
        val late = bill("2026-11-21T00:30:00Z", total = 30_000)
        val evening = bill("2026-11-20T21:30:00Z", total = 10_000)

        val rows = RollupCalculator.compute(listOf(evening, late), emptyList(), day("2026-11-20"), zone).hourlySales

        assertEquals(listOf(1, 22), rows.map { it.hour })
        assertEquals(30_000, rows.first { it.hour == 1 }.revenueMinor)
        assertEquals(10_000, rows.first { it.hour == 22 }.revenueMinor)
    }

    @Test
    fun `the repeated DST hour is one bucket`() {
        // 2026-10-25: 02:30 local happens twice, at 00:30Z (+02:00) and 01:30Z (+01:00).
        val first = bill("2026-10-25T00:30:00Z")
        val second = bill("2026-10-25T01:30:00Z")

        val rows = RollupCalculator.compute(listOf(first, second), emptyList(), day("2026-10-24"), zone).hourlySales

        assertEquals(listOf(2), rows.map { it.hour })
        assertEquals(2, rows.single().bills)
    }

    @Test
    fun `tips come off card first, then cash (M14)`() {
        val mixed =
            bill(
                "2026-11-20T20:00:00Z",
                total = 10_000,
                tips = 1_000,
                payments = listOf(RollupPayment("CARD", 6_000), RollupPayment("CASH", 5_000)),
            )
        val cashOnly = bill("2026-11-20T20:10:00Z", total = 4_000, tips = 500, payments = listOf(RollupPayment("CASH", 4_500)))

        val row = RollupCalculator.compute(listOf(mixed, cashOnly), emptyList(), day("2026-11-20"), zone).hourlySales.single()

        assertEquals(5_000, row.cardMinor)
        assertEquals(9_000, row.cashMinor)
        assertEquals(row.revenueMinor, row.cardMinor + row.cashMinor + row.otherMinor)
        assertEquals(1_500, row.tipsMinor)
    }

    @Test
    fun `deleted bills count only as deleted, refunded bills stay in their paid day (M13)`() {
        val deleted = bill("2026-11-20T20:00:00Z", total = 7_000, deleted = true)
        val refunded = bill("2026-11-20T20:05:00Z", total = 3_000, refunded = true)
        val paid = bill("2026-11-20T20:10:00Z", total = 10_000)

        val row = RollupCalculator.compute(listOf(deleted, refunded, paid), emptyList(), day("2026-11-20"), zone).hourlySales.single()

        assertEquals(13_000, row.revenueMinor)
        assertEquals(2, row.bills)
        assertEquals(3_000, row.refundedMinor)
        assertEquals(1, row.deletedBills)
        assertEquals(7_000, row.deletedMinor)
    }

    @Test
    fun `a bill without a guest count counts one guest (M5)`() {
        val rows =
            RollupCalculator.compute(
                listOf(bill("2026-11-20T20:00:00Z", guests = null), bill("2026-11-20T20:05:00Z", guests = 4)),
                emptyList(),
                day("2026-11-20"),
                zone,
            )

        assertEquals(5, rows.hourlySales.single().guests)
    }

    @Test
    fun `staff sales go to who opened the bill, tips to who closed it`() {
        val handedOver = bill("2026-11-20T20:00:00Z", total = 10_000, tips = 800, createdBy = staffA, paidBy = staffB)

        val staff = RollupCalculator.compute(listOf(handedOver), emptyList(), day("2026-11-20"), zone).staffDaily.associateBy { it.staffId }

        assertEquals(10_000, staff.getValue(staffA).revenueMinor)
        assertEquals(0, staff.getValue(staffA).tipsMinor)
        assertEquals(0, staff.getValue(staffB).revenueMinor)
        assertEquals(800, staff.getValue(staffB).tipsMinor)
    }

    @Test
    fun `table time runs from opened to paid, clipped to the business day`() {
        // Opened 03:00 local on the previous date (before this day's 05:00 start), paid 05:30 local.
        val early = bill("2026-11-20T04:30:00Z", opened = "2026-11-20T02:00:00Z")
        val evening = bill("2026-11-20T20:00:00Z", opened = "2026-11-20T19:15:00Z")

        val row = RollupCalculator.compute(listOf(early, evening), emptyList(), day("2026-11-20"), zone).deskDaily.single()

        assertEquals(30 + 45, row.occupiedMinutes)
        assertEquals(2, row.bills)
    }

    @Test
    fun `products and opened bills count in the hour the bill was opened, deleted ones not at all`() {
        val opened8pm = bill("2026-11-20T21:30:00Z", opened = "2026-11-20T19:10:00Z")
        val deleted = bill("2026-11-20T21:40:00Z", opened = "2026-11-20T19:20:00Z", deleted = true)
        val lines =
            listOf(
                RollupLine(opened8pm.billId, product, BigDecimal("2"), 0, 13_800, emptyList()),
                RollupLine(deleted.billId, product, BigDecimal("5"), 0, 34_500, emptyList()),
            )

        val rollups = RollupCalculator.compute(listOf(opened8pm, deleted), lines, day("2026-11-20"), zone)

        val row = rollups.productHourly.single()
        assertEquals(20, row.hour)
        assertEquals(0, BigDecimal("2").compareTo(row.quantity))
        assertEquals(13_800, row.grossMinor)
        assertEquals(listOf(20), rollups.hourlyActivity.map { it.hour })
        assertEquals(1, rollups.hourlyActivity.single().sessions)
    }

    @Test
    fun `discounts are grouped by their raw type`() {
        val b = bill("2026-11-20T20:00:00Z")
        val lines =
            listOf(
                RollupLine(b.billId, product, BigDecimal.ONE, 1_380, 5_520, listOf(RollupDiscount("HAPPY_HOUR", 1_380))),
                RollupLine(b.billId, product, BigDecimal.ONE, 690, 6_210, listOf(RollupDiscount("HAPPY_HOUR", 690))),
                RollupLine(b.billId, product, BigDecimal.ONE, 3_450, 3_450, listOf(RollupDiscount(null, 3_450))),
            )

        val rows = RollupCalculator.compute(listOf(b), lines, day("2026-11-20"), zone).discountDaily.associateBy { it.discountType }

        assertEquals(2, rows.getValue("HAPPY_HOUR").records)
        assertEquals(2_070, rows.getValue("HAPPY_HOUR").amountMinor)
        assertEquals(3_450, rows.getValue("UNSPECIFIED").amountMinor)
    }

    @Test
    fun `revenue is payments without tips, so card plus cash plus other is revenue`() {
        // The bill total says 9 900, but the payments (without the 500 tip) are 10 000: revenue follows the payments.
        val paid = bill("2026-11-20T20:00:00Z", total = 9_900, tips = 500, payments = listOf(RollupPayment("CARD", 10_500)))

        val row = RollupCalculator.compute(listOf(paid), emptyList(), day("2026-11-20"), zone).hourlySales.single()

        assertEquals(10_000, row.revenueMinor)
        assertEquals(row.revenueMinor, row.cardMinor + row.cashMinor + row.otherMinor)
    }

    @Test
    fun `the net revenue leaves out bills without a net total and counts them, never as 0`() {
        val withNet = bill("2026-11-20T20:00:00Z", total = 12_100, net = 10_000)
        val withoutNet = bill("2026-11-20T20:05:00Z", total = 5_000, net = null)

        val row = RollupCalculator.compute(listOf(withNet, withoutNet), emptyList(), day("2026-11-20"), zone).hourlySales.single()

        assertEquals(10_000, row.revenueWithoutTaxMinor)
        assertEquals(1, row.billsMissingNet)
        assertEquals(17_100, row.revenueMinor)
    }

    @Test
    fun `a stored discount without an amount fails the day instead of counting 0`() {
        val b = bill("2026-11-20T20:00:00Z")
        val lines = listOf(RollupLine(b.billId, product, BigDecimal.ONE, 0, 6_900, listOf(RollupDiscount("HAPPY_HOUR", null))))

        assertFailsWith<IllegalStateException> { RollupCalculator.compute(listOf(b), lines, day("2026-11-20"), zone) }
    }
}
