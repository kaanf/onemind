package com.onemind.backend.reporting.application

import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.BillFact
import com.onemind.backend.pos.api.BillLineFact
import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.RollupBill
import com.onemind.backend.reporting.domain.RollupCalculator
import com.onemind.backend.reporting.domain.RollupDiscount
import com.onemind.backend.reporting.domain.RollupLine
import com.onemind.backend.reporting.domain.RollupPayment
import com.onemind.backend.reporting.domain.RollupVersion
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Recomputes a venue's rollups for given business days from the POS facts (ADR 0013 §5): one
 * transaction per day, under a lock on that (venue, day), reads the facts and replaces the day's rows. Idempotent: the same facts give the same rows, so a
 * duplicate event or a rebuild changes nothing. A day whose last bill moved away ends up empty.
 */
@Service
class RefreshRollups(
    private val pos: PosReadModel,
    private val venueRegistry: VenueRegistry,
    private val rollups: RollupRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Recomputes [days] of [venueId]; nothing if the venue is gone (e.g. purged). */
    fun refresh(
        venueId: UUID,
        days: Collection<LocalDate>,
    ) {
        val settings = venueRegistry.businessDay(venueId) ?: return
        days.distinct().sorted().forEach { day ->
            // Locked first, read inside the lock: the last of two concurrent refreshes writes the newest facts.
            transactions.executeWithoutResult {
                rollups.lockDay(venueId, day)
                val bills = allPages { after -> pos.bills(venueId, listOf(day), after).let { it.items to it.next } }
                val lines = allPages { after -> pos.billLines(venueId, listOf(day), after).let { it.items to it.next } }
                val range = BusinessDayCalendar.range(day, settings.zone, settings.businessDayStart)
                val computed =
                    try {
                        RollupCalculator.compute(bills.map { it.toRollup() }, lines.map { it.toRollup() }, range, settings.zone)
                    } catch (e: IllegalStateException) {
                        log.error("Rollup refresh failed: venueId={}, businessDay={}, error={}", venueId, day, e.message)
                        throw e
                    }
                if (computed.paymentMismatchBillIds.isNotEmpty()) {
                    log.warn(
                        "Bills left out of revenue, payments don't add up: venueId={}, businessDay={}, billIds={}",
                        venueId,
                        day,
                        computed.paymentMismatchBillIds,
                    )
                }
                rollups.replaceDay(venueId, day, computed, bills.maxOfOrNull { it.modifiedAt }, RollupVersion.CURRENT, clock.instant())
            }
        }
    }

    private fun <T> allPages(page: (UUID?) -> Pair<List<T>, UUID?>): List<T> {
        val all = mutableListOf<T>()
        var after: UUID? = null
        do {
            val (items, next) = page(after)
            all += items
            after = next
        } while (after != null)
        return all
    }

    private fun BillFact.toRollup() =
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

    private fun BillLineFact.toRollup() =
        RollupLine(
            billId = billId,
            productId = productId,
            quantity = quantity,
            discountMinor = discountMinor,
            totalMinor = totalMinor,
            discounts = discounts.map { RollupDiscount(it.type, it.amountMinor) },
        )
}
