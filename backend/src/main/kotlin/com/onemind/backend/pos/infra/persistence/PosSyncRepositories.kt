package com.onemind.backend.pos.infra.persistence

import com.onemind.db.pos.tables.references.POS_CONNECTION
import com.onemind.db.pos.tables.references.SYNC_CURSOR
import com.onemind.db.pos.tables.references.SYNC_JOB
import com.onemind.db.pos.tables.references.VENUE_LINK
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL.excluded
import org.jooq.impl.DSL.jsonbArray
import org.jooq.impl.DSL.jsonbObject
import org.jooq.impl.DSL.key
import org.jooq.impl.DSL.value
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class PosConnectionRow(
    val id: UUID,
    val organizationId: UUID,
    val provider: String,
    val externalMerchantId: String,
)

@Repository
class PosConnectionRepository(
    private val dsl: DSLContext,
) {
    /** Connections the sync reads: all but revoked ones. */
    fun syncable(): List<PosConnectionRow> {
        val c = POS_CONNECTION
        return dsl
            .select(c.ID, c.ORGANIZATION_ID, c.PROVIDER, c.EXTERNAL_MERCHANT_ID)
            .from(c)
            .where(c.STATUS.ne(REVOKED))
            .orderBy(c.ID)
            .fetch()
            // Kotlin's inline map, not jOOQ's RecordMapper: records stay inside this method (R4).
            .toList()
            .map { r ->
                PosConnectionRow(
                    r.required(c.ID),
                    r.required(c.ORGANIZATION_ID),
                    r.required(c.PROVIDER),
                    r.required(c.EXTERNAL_MERCHANT_ID),
                )
            }
    }

    /** A new connection of [organizationId]; `PENDING` until its first sync. */
    fun insert(
        organizationId: UUID,
        provider: String,
        externalMerchantId: String,
    ): PosConnectionRow {
        val c = POS_CONNECTION
        val id =
            dsl
                .insertInto(c)
                .set(c.ORGANIZATION_ID, organizationId)
                .set(c.PROVIDER, provider)
                .set(c.EXTERNAL_MERCHANT_ID, externalMerchantId)
                .returning(c.ID)
                .fetchOne(c.ID)
                ?: error("insert returned no id")
        return PosConnectionRow(id, organizationId, provider, externalMerchantId)
    }

    fun forOrganization(organizationId: UUID): PosConnectionRow? {
        val c = POS_CONNECTION
        return dsl
            .select(c.ID, c.ORGANIZATION_ID, c.PROVIDER, c.EXTERNAL_MERCHANT_ID)
            .from(c)
            .where(c.ORGANIZATION_ID.eq(organizationId))
            .orderBy(c.ID)
            .limit(1)
            .fetchOne()
            ?.let { r ->
                PosConnectionRow(
                    r.required(c.ID),
                    r.required(c.ORGANIZATION_ID),
                    r.required(c.PROVIDER),
                    r.required(c.EXTERNAL_MERCHANT_ID),
                )
            }
    }

    fun markSynced(
        connectionId: UUID,
        now: Instant,
    ) {
        val c = POS_CONNECTION
        dsl
            .update(c)
            .set(c.STATUS, ACTIVE)
            .setNull(c.LAST_ERROR)
            .set(c.LAST_SYNCED_AT, now)
            .set(c.UPDATED_AT, now)
            // A revoked connection stays revoked, whatever a sync still running reports.
            .where(c.ID.eq(connectionId), c.STATUS.ne(REVOKED))
            .execute()
    }

    /** [error] is a short code, never POS response text (it may hold personal data). */
    fun markFailed(
        connectionId: UUID,
        error: String,
        now: Instant,
    ) {
        val c = POS_CONNECTION
        dsl
            .update(c)
            .set(c.STATUS, ERROR)
            .set(c.LAST_ERROR, error)
            .set(c.UPDATED_AT, now)
            // A revoked connection stays revoked, whatever a sync still running reports.
            .where(c.ID.eq(connectionId), c.STATUS.ne(REVOKED))
            .execute()
    }

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val ERROR = "ERROR"
        const val REVOKED = "REVOKED"
    }
}

data class VenueLinkRow(
    val venueId: UUID,
    val externalPlaceId: String,
    val active: Boolean,
)

@Repository
class VenueLinkRepository(
    private val dsl: DSLContext,
) {
    fun linked(connectionId: UUID): List<VenueLinkRow> =
        dsl
            .select(VENUE_LINK.VENUE_ID, VENUE_LINK.EXTERNAL_PLACE_ID, VENUE_LINK.ACTIVE)
            .from(VENUE_LINK)
            .where(VENUE_LINK.CONNECTION_ID.eq(connectionId))
            .fetch()
            // Kotlin's inline map, not jOOQ's RecordMapper: records stay inside this method (R4).
            .toList()
            .map { r ->
                VenueLinkRow(r.required(VENUE_LINK.VENUE_ID), r.required(VENUE_LINK.EXTERNAL_PLACE_ID), r.required(VENUE_LINK.ACTIVE))
            }

    fun insert(
        venueId: UUID,
        connectionId: UUID,
        externalPlaceId: String,
    ) {
        dsl
            .insertInto(VENUE_LINK)
            .set(VENUE_LINK.VENUE_ID, venueId)
            .set(VENUE_LINK.CONNECTION_ID, connectionId)
            .set(VENUE_LINK.EXTERNAL_PLACE_ID, externalPlaceId)
            .execute()
    }

    fun setActive(
        venueId: UUID,
        active: Boolean,
    ) {
        dsl
            .update(VENUE_LINK)
            .set(VENUE_LINK.ACTIVE, active)
            .where(VENUE_LINK.VENUE_ID.eq(venueId), VENUE_LINK.ACTIVE.ne(active))
            .execute()
    }
}

/** When a resource last succeeded, and when it last failed if its last attempt failed. */
data class SyncCursorState(
    val lastSuccessAt: Instant?,
    val failedAt: Instant?,
)

/** Per venue and resource (`BILLS`, `MENU`, `DESKS`): the last success, the last error and a cursor. */
@Repository
class SyncCursorRepository(
    private val dsl: DSLContext,
) {
    fun state(
        venueId: UUID,
        resource: String,
    ): SyncCursorState? {
        val s = SYNC_CURSOR
        return dsl
            .select(s.LAST_SUCCESS_AT, s.LAST_ERROR, s.UPDATED_AT)
            .from(s)
            .where(s.VENUE_ID.eq(venueId), s.RESOURCE.eq(resource))
            .fetchOne()
            ?.let { r -> SyncCursorState(r.get(s.LAST_SUCCESS_AT), r.get(s.UPDATED_AT)?.takeIf { r.get(s.LAST_ERROR) != null }) }
    }

    fun cursor(
        venueId: UUID,
        resource: String,
    ): String? =
        dsl
            .select(SYNC_CURSOR.CURSOR)
            .from(SYNC_CURSOR)
            .where(SYNC_CURSOR.VENUE_ID.eq(venueId), SYNC_CURSOR.RESOURCE.eq(resource))
            .fetchOne(SYNC_CURSOR.CURSOR)

    /** Stores how far the sync of [resource] got; runs in the transaction that wrote up to there. */
    fun moveCursor(
        venueId: UUID,
        resource: String,
        cursor: String,
        now: Instant,
    ) {
        val s = SYNC_CURSOR
        dsl
            .insertInto(s)
            .set(s.VENUE_ID, venueId)
            .set(s.RESOURCE, resource)
            .set(s.CURSOR, cursor)
            .onConflict(s.VENUE_ID, s.RESOURCE)
            .doUpdate()
            .set(s.CURSOR, excluded(s.CURSOR))
            .set(s.UPDATED_AT, now)
            .execute()
    }

    fun succeeded(
        venueId: UUID,
        resource: String,
        now: Instant,
    ) {
        val s = SYNC_CURSOR
        dsl
            .insertInto(s)
            .set(s.VENUE_ID, venueId)
            .set(s.RESOURCE, resource)
            .set(s.LAST_SUCCESS_AT, now)
            .onConflict(s.VENUE_ID, s.RESOURCE)
            .doUpdate()
            .set(s.LAST_SUCCESS_AT, excluded(s.LAST_SUCCESS_AT))
            .setNull(s.LAST_ERROR)
            .set(s.UPDATED_AT, now)
            .execute()
    }

    /** [error] is a short code, never POS response text. */
    fun failed(
        venueId: UUID,
        resource: String,
        error: String,
        now: Instant,
    ) {
        val s = SYNC_CURSOR
        dsl
            .insertInto(s)
            .set(s.VENUE_ID, venueId)
            .set(s.RESOURCE, resource)
            .set(s.LAST_ERROR, error)
            .set(s.UPDATED_AT, now)
            .onConflict(s.VENUE_ID, s.RESOURCE)
            .doUpdate()
            .set(s.LAST_ERROR, excluded(s.LAST_ERROR))
            .set(s.UPDATED_AT, now)
            .execute()
    }
}

/** One step of a sync run, shown later by `/pos/sync/status`; [error] is a short code. */
data class SyncStepRow(
    val name: String,
    val status: String,
    val error: String? = null,
)

@Repository
class SyncJobRepository(
    private val dsl: DSLContext,
) {
    fun start(
        connectionId: UUID,
        kind: String,
        now: Instant,
    ): UUID =
        dsl
            .insertInto(SYNC_JOB)
            .set(SYNC_JOB.CONNECTION_ID, connectionId)
            .set(SYNC_JOB.KIND, kind)
            .set(SYNC_JOB.STATUS, RUNNING)
            .set(SYNC_JOB.STARTED_AT, now)
            .returning(SYNC_JOB.ID)
            .fetchOne(SYNC_JOB.ID)
            ?: error("insert returned no id")

    fun finish(
        jobId: UUID,
        steps: List<SyncStepRow>,
        now: Instant,
    ) {
        val status = if (steps.all { it.status == SUCCEEDED }) SUCCEEDED else FAILED
        val json =
            jsonbArray(
                steps.map { step ->
                    jsonbObject(key("name").value(step.name), key("status").value(step.status), key("error").value(value(step.error)))
                },
            )
        dsl
            .update(SYNC_JOB)
            .set(SYNC_JOB.STATUS, status)
            .set(SYNC_JOB.STEPS, json)
            .set(SYNC_JOB.FINISHED_AT, now)
            .set(SYNC_JOB.UPDATED_AT, now)
            .where(SYNC_JOB.ID.eq(jobId))
            .execute()
    }

    companion object {
        const val RUNNING = "RUNNING"
        const val SUCCEEDED = "SUCCEEDED"
        const val FAILED = "FAILED"
    }
}

private fun <T> Record.required(field: Field<T?>): T = get(field) ?: error("${field.name} is null")
