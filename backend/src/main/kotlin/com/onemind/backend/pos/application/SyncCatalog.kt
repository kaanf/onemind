package com.onemind.backend.pos.application

import com.onemind.backend.pos.domain.CatalogCategory
import com.onemind.backend.pos.domain.CatalogDesk
import com.onemind.backend.pos.domain.CatalogProduct
import com.onemind.backend.pos.domain.CatalogSection
import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.PosCatalogRepository
import com.onemind.backend.pos.infra.persistence.SyncCursorRepository
import com.onemind.backend.pos.infra.persistence.SyncStepRow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.Currency

/**
 * Menu and desks of an active venue, at most once per [INTERVAL] each (STORYOUS.md, sync strategy).
 * The POS call runs before the transaction; a failure is recorded on the venue's `sync_cursor` and
 * doesn't stop the other resource or venue.
 */
@Service
class SyncCatalog(
    private val catalog: PosCatalogRepository,
    private val cursors: SyncCursorRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun runIfDue(
        venue: LinkedVenue,
        place: PosPlaceRef,
        currency: Currency,
        provider: PosProvider,
    ): List<SyncStepRow> =
        listOfNotNull(
            step(venue, MENU) {
                val menu = provider.menu(place)
                val categories = menu.categories.map { CatalogCategory(it.categoryId, it.name, it.parentId) }
                val products =
                    menu.products.map {
                        CatalogProduct(
                            externalId = it.productId,
                            categoryExternalId = it.categoryId,
                            name = it.name,
                            measure = it.measure,
                            ean = it.ean,
                            vatRate = it.vatRate?.toBigDecimal(),
                            priceMinor = it.price?.let { price -> PosMoney.minorUnits(price, currency) },
                            active = it.enabled,
                        )
                    }
                transactions.executeWithoutResult { catalog.replaceMenu(venue.venueId, categories, products, clock.instant()) }
            },
            step(venue, DESKS) {
                val view = provider.deskView(place)
                val sections = view.sections.map { CatalogSection(it.sectionId, it.name, it.color) }
                val desks = view.desks.map { CatalogDesk(it.deskId, it.sectionId, it.name, it.code, it.virtual, it.removed) }
                transactions.executeWithoutResult { catalog.replaceDesks(venue.venueId, sections, desks, clock.instant()) }
            },
        )

    /** Runs [sync] if [resource] is due, records the outcome; `null` if it wasn't due. */
    private fun step(
        venue: LinkedVenue,
        resource: String,
        sync: () -> Unit,
    ): SyncStepRow? {
        val now = clock.instant()
        val last = cursors.lastSuccess(venue.venueId, resource)
        if (last != null && last.isAfter(now - INTERVAL)) return null
        val name = "${resource.lowercase()}:${venue.venueId}"
        return try {
            sync()
            cursors.succeeded(venue.venueId, resource, clock.instant())
            SyncStepRow(name, SUCCEEDED)
        } catch (e: RuntimeException) {
            val error = e.javaClass.simpleName
            log.warn("POS {} sync failed: venueId={}, error={}", resource, venue.venueId, error, e)
            cursors.failed(venue.venueId, resource, error, clock.instant())
            SyncStepRow(name, FAILED, error)
        }
    }

    companion object {
        const val MENU = "MENU"
        const val DESKS = "DESKS"
        val INTERVAL: Duration = Duration.ofHours(1)
        private const val SUCCEEDED = "SUCCEEDED"
        private const val FAILED = "FAILED"
    }
}
