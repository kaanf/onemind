package com.onemind.backend.common.idempotency.infra

import com.onemind.db.platform.tables.references.IDEMPOTENCY_RECORD
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.excluded
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** What a request with an `Idempotency-Key` may do, decided by [IdempotencyRecordRepository.claim]. */
sealed interface IdempotencyClaim {
    /** The key is new (or its record expired or was abandoned): run the request. */
    data object Acquired : IdempotencyClaim

    /** The first request with this key is still running. */
    data object InProgress : IdempotencyClaim

    /** The key was used for a different request. */
    data object Mismatch : IdempotencyClaim

    /** The request already completed: send its response again. */
    data class Replay(
        val status: Int,
        val headers: Map<String, String>,
        val body: String?,
    ) : IdempotencyClaim
}

@Repository
class IdempotencyRecordRepository(
    private val dsl: DSLContext,
    private val json: JsonMapper,
) {
    /**
     * Inserts an `IN_PROGRESS` record for the key in one atomic statement, taking over a record that
     * expired, or one left `IN_PROGRESS` longer than [lockTimeout] by the same request (a crashed
     * instance). Otherwise reports what the existing record allows.
     */
    fun claim(
        userId: UUID,
        key: UUID,
        method: String,
        path: String,
        requestHash: ByteArray,
        now: Instant,
        ttl: Duration,
        lockTimeout: Duration,
    ): IdempotencyClaim {
        val r = IDEMPOTENCY_RECORD
        val acquired =
            dsl
                .insertInto(r)
                .set(r.USER_ID, userId)
                .set(r.KEY, key)
                .set(r.METHOD, method)
                .set(r.PATH, path)
                .set(r.REQUEST_HASH, requestHash)
                .set(r.STATUS, IN_PROGRESS)
                .set(r.CREATED_AT, now)
                .set(r.EXPIRES_AT, now + ttl)
                .onConflict(r.USER_ID, r.KEY)
                .doUpdate()
                .set(r.METHOD, excluded(r.METHOD))
                .set(r.PATH, excluded(r.PATH))
                .set(r.REQUEST_HASH, excluded(r.REQUEST_HASH))
                .set(r.STATUS, IN_PROGRESS)
                .setNull(r.RESPONSE_STATUS)
                .setNull(r.RESPONSE_HEADERS)
                .setNull(r.RESPONSE_BODY)
                .set(r.CREATED_AT, excluded(r.CREATED_AT))
                .set(r.EXPIRES_AT, excluded(r.EXPIRES_AT))
                .where(
                    r.EXPIRES_AT
                        .le(now)
                        .or(
                            r.STATUS
                                .eq(IN_PROGRESS)
                                .and(r.CREATED_AT.le(now - lockTimeout))
                                .and(r.REQUEST_HASH.eq(excluded(r.REQUEST_HASH))),
                        ),
                ).returning(r.KEY)
                .fetchOne() != null
        if (acquired) return IdempotencyClaim.Acquired

        // Gone between the two statements (released by a failed request): the client retries.
        val existing =
            dsl.selectFrom(r).where(r.USER_ID.eq(userId), r.KEY.eq(key)).fetchOne()
                ?: return IdempotencyClaim.InProgress
        return when {
            existing.method != method || existing.path != path || !requestHash.contentEquals(existing.requestHash) -> {
                IdempotencyClaim.Mismatch
            }

            existing.status == IN_PROGRESS -> {
                IdempotencyClaim.InProgress
            }

            else -> {
                IdempotencyClaim.Replay(
                    status = existing.responseStatus ?: error("COMPLETED record without status"),
                    headers = existing.responseHeaders?.let { json.readValue<Map<String, String>>(it.data()) }.orEmpty(),
                    body = existing.responseBody,
                )
            }
        }
    }

    /** Stores the response of an acquired key. [body] must be JSON or null. */
    fun complete(
        userId: UUID,
        key: UUID,
        status: Int,
        headers: Map<String, String>,
        body: String?,
    ) {
        val r = IDEMPOTENCY_RECORD
        dsl
            .update(r)
            .set(r.STATUS, COMPLETED)
            .set(r.RESPONSE_STATUS, status)
            .set(r.RESPONSE_HEADERS, JSONB.valueOf(json.writeValueAsString(headers)))
            .set(r.RESPONSE_BODY, body)
            .where(r.USER_ID.eq(userId), r.KEY.eq(key), r.STATUS.eq(IN_PROGRESS))
            .execute()
    }

    /** Forgets an acquired key whose request failed, so the client can retry with it. */
    fun release(
        userId: UUID,
        key: UUID,
    ) {
        val r = IDEMPOTENCY_RECORD
        dsl.deleteFrom(r).where(r.USER_ID.eq(userId), r.KEY.eq(key), r.STATUS.eq(IN_PROGRESS)).execute()
    }

    fun deleteExpired(now: Instant): Int {
        val r = IDEMPOTENCY_RECORD
        return dsl.deleteFrom(r).where(r.EXPIRES_AT.le(now)).execute()
    }

    private companion object {
        const val IN_PROGRESS = "IN_PROGRESS"
        const val COMPLETED = "COMPLETED"
    }
}
