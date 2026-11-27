package com.onemind.backend.reporting

import com.onemind.backend.reporting.domain.StaffCalculator
import com.onemind.backend.reporting.domain.StaffDailyRow
import com.onemind.backend.reporting.domain.VenueStaff
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaffCalculatorTest {
    private val venueA = UUID.fromString("00000000-0000-7000-8000-0000000000a1")
    private val venueB = UUID.fromString("00000000-0000-7000-8000-0000000000b1")
    private val anna = UUID.fromString("00000000-0000-7000-8000-0000000000f1")
    private val tomas = UUID.fromString("00000000-0000-7000-8000-0000000000f2")
    private val nameless = UUID.fromString("00000000-0000-7000-8000-0000000000f3")
    private val lucie = UUID.fromString("00000000-0000-7000-8000-0000000000f4")

    private fun row(
        staff: UUID,
        bills: Int,
        revenue: Long,
        guests: Int,
        tips: Long,
        deleted: Int = 0,
    ) = StaffDailyRow(staff, bills, revenue, guests, discountMinor = 0, deletedBills = deleted, tipsMinor = tips)

    @Test
    fun `one person's rows in two venues of the account are one row, and totals are the sums of the listed staff`() {
        val figures =
            StaffCalculator.compute(
                listOf(
                    VenueStaff(
                        venueA,
                        listOf(row(anna, 3, 120_000, 7, 9_000, deleted = 1), row(tomas, 2, 300_000, 4, 0)),
                        mapOf(anna to "Anna", tomas to "Tomáš"),
                    ),
                    VenueStaff(venueB, listOf(row(anna, 1, 50_000, 2, 1_000)), mapOf(anna to "Anna")),
                ),
            )

        assertEquals(listOf(tomas, anna), figures.staff.map { it.staffId })
        val a = figures.staff.single { it.staffId == anna }
        assertEquals(170_000, a.salesMinor)
        assertEquals(10_000, a.tipsMinor)
        assertEquals(9, a.guests)
        assertEquals(4, a.bills)
        assertEquals(1, a.deletedBills)
        assertEquals("Anna", a.name)
        assertEquals(470_000, figures.totals.salesMinor)
        assertEquals(10_000, figures.totals.tipsMinor)
        assertEquals(13, figures.totals.guests)
        assertEquals(6, figures.totals.bills)
    }

    @Test
    fun `equal sales sort by name with unnamed last, then by id`() {
        val figures =
            StaffCalculator.compute(
                listOf(
                    VenueStaff(
                        venueA,
                        listOf(row(nameless, 1, 1_000, 1, 0), row(tomas, 1, 1_000, 1, 0), row(anna, 1, 1_000, 1, 0)),
                        mapOf(anna to "Anna", tomas to "Tomáš", nameless to null),
                    ),
                ),
            )

        assertEquals(listOf(anna, tomas, nameless), figures.staff.map { it.staffId })
        assertNull(figures.staff.last().name)
    }

    @Test
    fun `a person with only tips (closed others' bills) is listed with zero sales`() {
        val figures = StaffCalculator.compute(listOf(VenueStaff(venueA, listOf(row(lucie, 0, 0, 0, 4_000)), mapOf(lucie to "Lucie"))))

        val l = figures.staff.single()
        assertEquals(0, l.salesMinor)
        assertEquals(4_000, l.tipsMinor)
    }

    @Test
    fun `a covered day without bills is an empty list with zero totals`() {
        val figures = StaffCalculator.compute(listOf(VenueStaff(venueA, emptyList(), emptyMap())))

        assertTrue(figures.staff.isEmpty())
        assertEquals(0, figures.totals.salesMinor)
        assertEquals(0, figures.totals.bills)
    }
}
