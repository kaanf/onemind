package com.onemind.backend.common.idempotency.infra

import com.onemind.db.platform.keys.IDEMPOTENCY_RECORD__IDEMPOTENCY_RECORD_USER_ID_FKEY
import com.onemind.db.platform.tables.references.IDEMPOTENCY_RECORD
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.excluded
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** What a request with an `Idempotency-Key` may do, decided by [IdempotencyRecordRepository.claim]. */
sealed interface IdempotencyClaim {
    /**
     * The key is new (or its record expired or was abandoned): run the request, then [complete] or
     * [release] with [claimId]. A request whose claim was taken over can no longer change the record.
     */
    data class Acquired(
        val claimId: UUID,
    ) : IdempotencyClaim

    /** The first request with this key is still running. */
    data object InProgress : IdempotencyClaim

    /** The key was used for a different request. */
    data object Mismatch : IdempotencyClaim

    /** The user doesn't exist (any more): a still-valid access token of a deleted account. */
    data object UnknownUser : IdempotencyClaim

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
        val claimId = UUID.randomUUID()
        val acquired =
            try {
                dsl
                    .insertInto(r)
                    .set(r.USER_ID, userId)
                    .set(r.KEY, key)
                    .set(r.METHOD, method)
                    .set(r.PATH, path)
                    .set(r.REQUEST_HASH, requestHash)
                    .set(r.CLAIM_ID, claimId)
                    .set(r.STATUS, IN_PROGRESS)
                    .set(r.CREATED_AT, now)
                    .set(r.EXPIRES_AT, now + ttl)
                    .onConflict(r.USER_ID, r.KEY)
                    .doUpdate()
                    .set(r.METHOD, excluded(r.METHOD))
                    .set(r.PATH, excluded(r.PATH))
                    .set(r.REQUEST_HASH, excluded(r.REQUEST_HASH))
                    .set(r.CLAIM_ID, excluded(r.CLAIM_ID))
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
            } catch (e: DataIntegrityViolationException) {
                if (violatesUserReference(e)) return IdempotencyClaim.UnknownUser
                throw e
            }
        if (acquired) return IdempotencyClaim.Acquired(claimId)

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
        claimId: UUID,
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
            .where(r.USER_ID.eq(userId), r.KEY.eq(key), r.CLAIM_ID.eq(claimId), r.STATUS.eq(IN_PROGRESS))
            .execute()
    }

    /** Forgets an acquired key whose request failed, so the client can retry with it. */
    fun release(
        userId: UUID,
        key: UUID,
        claimId: UUID,
    ) {
        val r = IDEMPOTENCY_RECORD
        dsl
            .deleteFrom(r)
            .where(r.USER_ID.eq(userId), r.KEY.eq(key), r.CLAIM_ID.eq(claimId), r.STATUS.eq(IN_PROGRESS))
            .execute()
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

/**
 * Whether [e] is the record's foreign key to `account.app_user` (SQLSTATE 23503 on that constraint),
 * as opposed to any other integrity error, which stays a server error. Reads the server's structured
 * error fields, so it doesn't depend on the language of PostgreSQL's messages.
 */
internal fun violatesUserReference(e: Throwable): Boolean =
    generateSequence(e) { it.cause }
        .filterIsInstance<PSQLException>()
        .any {
            it.sqlState == FOREIGN_KEY_VIOLATION &&
                it.serverErrorMessage?.constraint == IDEMPOTENCY_RECORD__IDEMPOTENCY_RECORD_USER_ID_FKEY.name
        }

private const val FOREIGN_KEY_VIOLATION = "23503"
