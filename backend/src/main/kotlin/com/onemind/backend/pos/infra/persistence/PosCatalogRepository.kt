package com.onemind.backend.pos.infra.persistence

import com.onemind.backend.pos.domain.CatalogCategory
import com.onemind.backend.pos.domain.CatalogDesk
import com.onemind.backend.pos.domain.CatalogProduct
import com.onemind.backend.pos.domain.CatalogSection
import com.onemind.db.pos.tables.references.CATEGORY
import com.onemind.db.pos.tables.references.DESK
import com.onemind.db.pos.tables.references.PRODUCT
import com.onemind.db.pos.tables.references.SECTION
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.select
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Menu and desks of a venue, upserted by `(venue_id, external_id)`. Rows the POS no longer reports are
 * flagged (`active = false`, `removed = true`), never deleted: bills point at them.
 */
@Repository
class PosCatalogRepository(
    private val dsl: DSLContext,
) {
    fun replaceMenu(
        venueId: UUID,
        categories: List<CatalogCategory>,
        products: List<CatalogProduct>,
        now: Instant,
    ) {
        val c = CATEGORY
        categories.forEach { category ->
            dsl
                .insertInto(c)
                .set(c.VENUE_ID, venueId)
                .set(c.EXTERNAL_ID, category.externalId)
                .set(c.NAME, category.name)
                .onConflict(c.VENUE_ID, c.EXTERNAL_ID)
                .doUpdate()
                .set(c.NAME, excluded(c.NAME))
                .set(c.UPDATED_AT, now)
                .execute()
        }
        // Parents once every category of the venue exists.
        categories.forEach { category ->
            dsl
                .update(c)
                .set(c.PARENT_ID, categoryId(venueId, category.parentExternalId))
                .where(c.VENUE_ID.eq(venueId), c.EXTERNAL_ID.eq(category.externalId))
                .execute()
        }

        val p = PRODUCT
        products.forEach { product ->
            dsl
                .insertInto(p)
                .set(p.VENUE_ID, venueId)
                .set(p.EXTERNAL_ID, product.externalId)
                .set(p.CATEGORY_ID, categoryId(venueId, product.categoryExternalId))
                .set(p.NAME, product.name)
                .set(p.MEASURE, product.measure)
                .set(p.EAN, product.ean)
                .set(p.VAT_RATE, product.vatRate)
                .set(p.PRICE_MINOR, product.priceMinor)
                .set(p.ACTIVE, product.active)
                .onConflict(p.VENUE_ID, p.EXTERNAL_ID)
                .doUpdate()
                .set(p.CATEGORY_ID, excluded(p.CATEGORY_ID))
                .set(p.NAME, excluded(p.NAME))
                .set(p.MEASURE, excluded(p.MEASURE))
                .set(p.EAN, excluded(p.EAN))
                .set(p.VAT_RATE, excluded(p.VAT_RATE))
                .set(p.PRICE_MINOR, excluded(p.PRICE_MINOR))
                .set(p.ACTIVE, excluded(p.ACTIVE))
                .set(p.UPDATED_AT, now)
                .execute()
        }
        dsl
            .update(p)
            .set(p.ACTIVE, false)
            .set(p.UPDATED_AT, now)
            .where(p.VENUE_ID.eq(venueId), p.ACTIVE.isTrue, p.EXTERNAL_ID.notIn(products.map { it.externalId }))
            .execute()
    }

    fun replaceDesks(
        venueId: UUID,
        sections: List<CatalogSection>,
        desks: List<CatalogDesk>,
        now: Instant,
    ) {
        val s = SECTION
        sections.forEach { section ->
            dsl
                .insertInto(s)
                .set(s.VENUE_ID, venueId)
                .set(s.EXTERNAL_ID, section.externalId)
                .set(s.NAME, section.name)
                .set(s.COLOR, section.color)
                .set(s.REMOVED, false)
                .onConflict(s.VENUE_ID, s.EXTERNAL_ID)
                .doUpdate()
                .set(s.NAME, excluded(s.NAME))
                .set(s.COLOR, excluded(s.COLOR))
                .set(s.REMOVED, false)
                .set(s.UPDATED_AT, now)
                .execute()
        }
        dsl
            .update(s)
            .set(s.REMOVED, true)
            .set(s.UPDATED_AT, now)
            .where(s.VENUE_ID.eq(venueId), s.REMOVED.isFalse, s.EXTERNAL_ID.notIn(sections.map { it.externalId }))
            .execute()

        val d = DESK
        desks.forEach { desk ->
            dsl
                .insertInto(d)
                .set(d.VENUE_ID, venueId)
                .set(d.EXTERNAL_ID, desk.externalId)
                .set(d.SECTION_ID, sectionId(venueId, desk.sectionExternalId))
                .set(d.NAME, desk.name)
                .set(d.CODE, desk.code)
                .set(d.IS_VIRTUAL, desk.virtual)
                .set(d.REMOVED, desk.removed)
                .onConflict(d.VENUE_ID, d.EXTERNAL_ID)
                .doUpdate()
                .set(d.SECTION_ID, excluded(d.SECTION_ID))
                .set(d.NAME, excluded(d.NAME))
                .set(d.CODE, excluded(d.CODE))
                .set(d.IS_VIRTUAL, excluded(d.IS_VIRTUAL))
                .set(d.REMOVED, excluded(d.REMOVED))
                .set(d.UPDATED_AT, now)
                .execute()
        }
        dsl
            .update(d)
            .set(d.REMOVED, true)
            .set(d.UPDATED_AT, now)
            .where(d.VENUE_ID.eq(venueId), d.REMOVED.isFalse, d.EXTERNAL_ID.notIn(desks.map { it.externalId }))
            .execute()
    }

    private fun categoryId(
        venueId: UUID,
        externalId: String?,
    ) = field(select(CATEGORY.ID).from(CATEGORY).where(CATEGORY.VENUE_ID.eq(venueId), CATEGORY.EXTERNAL_ID.eq(externalId)))

    private fun sectionId(
        venueId: UUID,
        externalId: String?,
    ) = field(select(SECTION.ID).from(SECTION).where(SECTION.VENUE_ID.eq(venueId), SECTION.EXTERNAL_ID.eq(externalId)))
}
