package com.onemind.backend.reporting

import com.onemind.backend.reporting.domain.DiscountMismatch
import com.onemind.backend.reporting.domain.DiscountTypeTotals
import com.onemind.backend.reporting.domain.HourTotals
import com.onemind.backend.reporting.domain.LossCalculator
import com.onemind.backend.reporting.domain.LossShare
import com.onemind.backend.reporting.domain.TypedDiscountDay
import com.onemind.backend.reporting.domain.VenueLosses
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LossCalculatorTest {
    private val venueA = UUID.fromString("00000000-0000-7000-8000-0000000000a1")
    private val venueB = UUID.fromString("00000000-0000-7000-8000-0000000000b1")
    private val monday = LocalDate.parse("2026-11-16")
    private val tuesday = LocalDate.parse("2026-11-17")

    private fun hour(
        day: LocalDate,
        paid: Long = 100_000,
        discount: Long = 0,
        refunded: Long = 0,
        refundedBills: Int = 0,
        deleted: Long = 0,
        deletedBills: Int = 0,
    ) = HourTotals(
        day = day,
        hour = 20,
        revenueMinor = paid + refunded,
        refundedMinor = refunded,
        tipsMinor = 0,
        guests = 0,
        discountMinor = discount,
        deletedMinor = deleted,
        paymentMismatchBills = 0,
        paidBills = 0,
        refundedBills = refundedBills,
        deletedBills = deletedBills,
        paidCardMinor = 0,
        paidCashMinor = 0,
        paidOtherMinor = 0,
    )

    private fun typed(
        day: LocalDate,
        type: String,
        records: Int,
        amount: Long,
    ) = TypedDiscountDay(day, type, records, amount)

    @Test
    fun `possible loss adds bill-level discounts, refunds and deleted bills, with its share of revenue`() {
        val figures =
            LossCalculator.compute(
                listOf(
                    VenueLosses(
                        venueA,
                        listOf(
                            hour(
                                monday,
                                paid = 300_000,
                                discount = 15_000,
                                refunded = 3_000,
                                refundedBills = 1,
                                deleted = 2_100,
                                deletedBills = 2,
                            ),
                        ),
                        listOf(typed(monday, "HAPPY_HOUR", 10, 15_000)),
                    ),
                ),
                upToDate = true,
            )

        assertEquals(20_100, figures.loss.totalMinor)
        assertEquals(BigDecimal("6.7"), figures.share?.percent())
        assertEquals(10 + 1 + 2, figures.records)
        assertEquals(1, figures.refunds.records)
        assertEquals(2, figures.deletedBills.records)
        assertEquals(listOf(DiscountTypeTotals("HAPPY_HOUR", 10, 15_000)), figures.discountsByType)
    }

    @Test
    fun `bill-level discounts above the typed ones of a venue and day become one UNTYPED row, last`() {
        val figures =
            LossCalculator.compute(
                listOf(
                    VenueLosses(
                        venueA,
                        listOf(hour(monday, discount = 9_000), hour(tuesday, discount = 1_000)),
                        listOf(typed(monday, "STAFF", 2, 2_000), typed(monday, "HAPPY_HOUR", 5, 6_000), typed(tuesday, "STAFF", 1, 1_000)),
                    ),
                    VenueLosses(venueB, listOf(hour(monday, discount = 500)), emptyList()),
                ),
                upToDate = true,
            )

        assertEquals(
            listOf(
                DiscountTypeTotals("HAPPY_HOUR", 5, 6_000),
                DiscountTypeTotals("STAFF", 3, 3_000),
                DiscountTypeTotals(LossCalculator.UNTYPED, null, 1_000 + 500),
            ),
            figures.discountsByType,
        )
        assertEquals(figures.loss.discountsMinor, figures.discountsByType.sumOf { it.amountMinor })
        assertEquals(emptyList(), figures.discountMismatches)
    }

    @Test
    fun `typed discounts above the bill level show typed only and are a data gap, never clamped`() {
        val figures =
            LossCalculator.compute(
                listOf(VenueLosses(venueA, listOf(hour(monday, discount = 1_000)), listOf(typed(monday, "COMP", 1, 1_500)))),
                upToDate = true,
            )

        assertEquals(listOf(DiscountTypeTotals("COMP", 1, 1_500)), figures.discountsByType)
        assertEquals(1_000, figures.loss.discountsMinor)
        assertEquals(listOf(DiscountMismatch(venueA, monday)), figures.discountMismatches)
    }

    @Test
    fun `record counts are hidden while a day is stored by an older calculation (rule 2)`() {
        val figures =
            LossCalculator.compute(
                listOf(VenueLosses(venueA, listOf(hour(monday, refunded = 3_000)), emptyList())),
                upToDate = false,
            )

        assertNull(figures.records)
        assertNull(figures.refunds.records)
        assertEquals(3_000, figures.refunds.amountMinor)
    }

    @Test
    fun `no revenue gives no share, and the change is in percentage points`() {
        assertNull(LossCalculator.share(1_000, 0))
        assertEquals(BigDecimal("0.8"), LossCalculator.changePoints(LossShare(6_700, 100_000), LossShare(5_900, 100_000)))
        assertEquals(BigDecimal("-1.3"), LossCalculator.changePoints(LossShare(5_000, 100_000), LossShare(6_250, 100_000)))
        assertNull(LossCalculator.changePoints(LossShare(5_000, 100_000), null))
        // Rounded once from the exact shares: 6.666…% − 6.62% = 0.046… → 0.0, while 6.7 − 6.6 would say 0.1.
        assertEquals(BigDecimal("0.0"), LossCalculator.changePoints(LossShare(2, 30), LossShare(662, 10_000)))
    }
}
