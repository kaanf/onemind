package com.onemind.backend.pos.application

import com.onemind.backend.account.api.VenueBusinessDay
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.events.BillsSynced
import com.onemind.backend.pos.domain.BillSyncCursor
import com.onemind.backend.pos.domain.QuarantineReason
import com.onemind.backend.pos.domain.SyncedBill
import com.onemind.backend.pos.domain.UnreadableBillException
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.PosBillRepository
import com.onemind.backend.pos.infra.persistence.SyncCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * `onemind.pos.sync`: [initialDays] business days are imported when a venue is first synced, at most
 * [initialDaysPerRun] of them per run (so other venues aren't held up); incremental runs look back
 * [lookback] before the cursor, so a bill whose `lastModifiedAt` lands a little late isn't missed.
 */
@ConfigurationProperties("onemind.pos.sync")
data class PosSyncProperties(
    val initialDays: Long = 90,
    val initialDaysPerRun: Long = 14,
    val lookback: Duration = Duration.ofMinutes(10),
) {
    init {
        require(initialDays in 1..MAX_INITIAL_DAYS) { "onemind.pos.sync.initial-days must be 1..$MAX_INITIAL_DAYS" }
        require(initialDaysPerRun >= 1) { "onemind.pos.sync.initial-days-per-run must be at least 1" }
        require(!lookback.isNegative) { "onemind.pos.sync.lookback must not be negative" }
    }

    private companion object {
        const val MAX_INITIAL_DAYS = 366L
    }
}

class VenueNotRegisteredException : RuntimeException("The linked venue doesn't exist")

/**
 * Paid bills of a venue (STORYOUS.md, sync strategy). First an initial import, one business day per
 * `from`/`till` window, oldest first, capped per run and resumable: the cursor moves after each finished
 * day. Then incremental: `modifiedSince` = cursor − lookback (inclusive), deleted bills included (a loss
 * component). For each page, bills new or changed since they were stored get their detail from the POS,
 * all **before** one transaction that writes them, quarantines the ones that can't be read exactly, moves
 * the cursor and publishes [BillsSynced] for the business days written (old and new), only if something
 * was written. Quarantined bills are retried every run, then hourly after a few attempts.
 */
@Service
class SyncBills(
    private val bills: PosBillRepository,
    private val cursors: SyncCursorRepository,
    private val venueRegistry: VenueRegistry,
    private val events: ApplicationEventPublisher,
    private val properties: PosSyncProperties,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** What one bills sync needs about the venue and its connection. */
    private class Target(
        val connectionId: UUID,
        val venueId: UUID,
        val place: PosPlaceRef,
        val settings: VenueBusinessDay,
        val provider: PosProvider,
    )

    fun run(
        connectionId: UUID,
        venue: LinkedVenue,
        place: PosPlaceRef,
        provider: PosProvider,
    ) {
        val settings = venueRegistry.businessDay(venue.venueId) ?: throw VenueNotRegisteredException()
        val target = Target(connectionId, venue.venueId, place, settings, provider)
        when (val cursor = BillSyncCursor.parse(cursors.cursor(venue.venueId, BILLS))) {
            null -> {
                val today = BusinessDayCalendar.today(clock, settings.zone, settings.businessDayStart)
                importDays(target, BillSyncCursor.Initial(today.minusDays(properties.initialDays), clock.instant()))
            }

            is BillSyncCursor.Initial -> {
                importDays(target, cursor)
            }

            is BillSyncCursor.Since -> {
                syncChanges(target, cursor.since)
            }
        }
        retryQuarantined(target)
        cursors.succeeded(venue.venueId, BILLS, clock.instant())
    }

    private fun importDays(
        target: Target,
        start: BillSyncCursor.Initial,
    ) {
        val zone = target.settings.zone
        val cutOff = target.settings.businessDayStart
        val today = BusinessDayCalendar.today(clock, zone, cutOff)
        var day = start.nextDay
        var imported = 0L
        while (!day.isAfter(today) && imported < properties.initialDaysPerRun) {
            val range = BusinessDayCalendar.range(day, zone, cutOff)
            val next =
                if (day == today) BillSyncCursor.Since(start.startedAt) else BillSyncCursor.Initial(day.plusDays(1), start.startedAt)
            pages(target, PosBillQuery(from = range.start, till = range.end, includeDeleted = true)) { page, last ->
                write(target, changedIds(target, page), cursorAfter = next.takeIf { last })
            }
            day = day.plusDays(1)
            imported++
        }
    }

    private fun syncChanges(
        target: Target,
        since: Instant,
    ) {
        var cursor = since
        pages(target, PosBillQuery(modifiedSince = since - properties.lookback, includeDeleted = true)) { page, _ ->
            cursor = maxOf(cursor, page.maxOfOrNull { it.lastModifiedAt.toInstant() } ?: cursor)
            write(target, changedIds(target, page), cursorAfter = BillSyncCursor.Since(cursor))
        }
    }

    private fun retryQuarantined(target: Target) {
        val due = bills.quarantineDueForRetry(target.venueId, clock.instant(), EAGER_ATTEMPTS, QUARANTINE_BACKOFF, RETRY_LIMIT)
        if (due.isNotEmpty()) write(target, due, cursorAfter = null)
    }

    /** Bills of [page] that are new or changed since they were stored. */
    private fun changedIds(
        target: Target,
        page: List<PosBill>,
    ): List<String> {
        val stored = bills.stored(target.venueId, page.map { it.billId })
        return page.filter { stored[it.billId]?.externalModifiedAt != it.lastModifiedAt.toInstant() }.map { it.billId }
    }

    /** Calls [handle] for every page of [query], telling it whether it is the last one. */
    private fun pages(
        target: Target,
        query: PosBillQuery,
        handle: (List<PosBill>, Boolean) -> Unit,
    ) {
        var lastBillId: String? = null
        do {
            val page = target.provider.bills(target.place, query.copy(lastBillId = lastBillId))
            handle(page.bills, page.nextPage == null)
            lastBillId = page.nextPage
        } while (lastBillId != null)
    }

    /** Reads [billIds] from the POS (outside any transaction), then writes them in one transaction. */
    private fun write(
        target: Target,
        billIds: List<String>,
        cursorAfter: BillSyncCursor?,
    ) {
        val readable = mutableListOf<SyncedBill>()
        val unreadable = mutableMapOf<String, QuarantineReason>()
        billIds.forEach { billId ->
            val detail = target.provider.billDetail(target.place, billId)
            if (detail == null) {
                unreadable[billId] = QuarantineReason.DETAIL_MISSING
            } else {
                try {
                    readable += PosBillMapping.toSynced(detail, target.settings.zone, target.settings.businessDayStart)
                } catch (e: UnreadableBillException) {
                    unreadable[billId] = e.reason
                }
            }
        }
        if (readable.isEmpty() && unreadable.isEmpty() && cursorAfter == null) return
        unreadable.forEach { (billId, reason) ->
            log.warn("POS bill quarantined: venueId={}, billId={}, reason={}", target.venueId, billId, reason)
        }
        transactions.executeWithoutResult {
            val now = clock.instant()
            val previous = bills.stored(target.venueId, readable.map { it.externalId })
            val staff = bills.upsertStaff(target.connectionId, readable.flatMap { listOfNotNull(it.createdBy, it.paidBy) }, now)
            val written = readable.filter { bills.upsert(target.venueId, it, staff, now) }
            bills.releaseQuarantine(target.venueId, readable.map { it.externalId })
            unreadable.forEach { (billId, reason) -> bills.quarantine(target.venueId, billId, reason.name, now) }
            cursorAfter?.let { cursors.moveCursor(target.venueId, BILLS, it.format(), now) }
            if (written.isNotEmpty()) {
                // A bill whose business day changed touches the old day too.
                val days = written.flatMap { listOfNotNull(it.businessDay, previous[it.externalId]?.businessDay) }
                events.publishEvent(BillsSynced(target.venueId, days.distinct().sorted()))
            }
        }
    }

    companion object {
        const val BILLS = "BILLS"
        private const val EAGER_ATTEMPTS = 3
        private val QUARANTINE_BACKOFF: Duration = Duration.ofHours(1)
        private const val RETRY_LIMIT = 50
    }
}
