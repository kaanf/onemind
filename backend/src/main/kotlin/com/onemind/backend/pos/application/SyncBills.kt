package com.onemind.backend.pos.application

import com.onemind.backend.account.api.VenueBusinessDay
import com.onemind.backend.account.api.VenueRegistry
import com.onemind.backend.common.time.BusinessDayCalendar
import com.onemind.backend.pos.api.events.BillsSynced
import com.onemind.backend.pos.domain.BillSyncCursor
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.PosBillRepository
import com.onemind.backend.pos.infra.persistence.SyncCursorRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** `onemind.pos.sync`: [initialDays] business days are imported when a venue is first synced. */
@ConfigurationProperties("onemind.pos.sync")
data class PosSyncProperties(
    val initialDays: Long = 90,
)

/**
 * Paid bills of a venue (STORYOUS.md, sync strategy). First an initial import, one business day per
 * `from`/`till` window, oldest first, resumable: the cursor moves after each finished day. Then
 * incremental: bills with `modifiedSince` the cursor (inclusive), deleted ones included (a loss
 * component). For each page, bills new or changed since they were stored get their detail (items)
 * from the POS, all **before** one transaction that writes them, moves the cursor and publishes
 * [BillsSynced] for the business days written, only if something was written.
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
        val settings = venueRegistry.businessDay(venue.venueId) ?: return
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
        while (!day.isAfter(today)) {
            val range = BusinessDayCalendar.range(day, zone, cutOff)
            val next =
                if (day == today) BillSyncCursor.Since(start.startedAt) else BillSyncCursor.Initial(day.plusDays(1), start.startedAt)
            pages(target, PosBillQuery(from = range.start, till = range.end, includeDeleted = true)) { page, last ->
                write(target, page, cursorAfter = next.takeIf { last })
            }
            day = day.plusDays(1)
        }
    }

    private fun syncChanges(
        target: Target,
        since: Instant,
    ) {
        var cursor = since
        pages(target, PosBillQuery(modifiedSince = since, includeDeleted = true)) { page, _ ->
            cursor = maxOf(cursor, page.maxOfOrNull { it.lastModifiedAt.toInstant() } ?: cursor)
            write(target, page, cursorAfter = BillSyncCursor.Since(cursor))
        }
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

    private fun write(
        target: Target,
        page: List<PosBill>,
        cursorAfter: BillSyncCursor?,
    ) {
        val stored = bills.modifiedAt(target.venueId, page.map { it.billId })
        val changed =
            page
                .filter { stored[it.billId] != it.lastModifiedAt.toInstant() }
                .mapNotNull { target.provider.billDetail(target.place, it.billId) }
                .map { PosBillMapping.toSynced(it, target.settings.zone, target.settings.businessDayStart) }
        if (changed.isEmpty() && cursorAfter == null) return
        transactions.executeWithoutResult {
            val now = clock.instant()
            val staff = bills.upsertStaff(target.connectionId, changed.flatMap { listOfNotNull(it.createdBy, it.paidBy) }, now)
            val written = changed.filter { bills.upsert(target.venueId, it, staff, now) }
            cursorAfter?.let { cursors.moveCursor(target.venueId, BILLS, it.format(), now) }
            if (written.isNotEmpty()) {
                events.publishEvent(BillsSynced(target.venueId, written.map { it.businessDay }.distinct().sorted()))
            }
        }
    }

    companion object {
        const val BILLS = "BILLS"
    }
}
