package com.onemind.backend.account.infra

import com.onemind.db.account.tables.references.APP_USER
import com.onemind.db.account.tables.references.EMAIL_LOGIN_CODE
import com.onemind.db.account.tables.references.REFRESH_TOKEN
import com.onemind.db.account.tables.references.USER_IDENTITY
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL.coalesce
import org.jooq.impl.DSL.excluded
import org.jooq.postgres.extensions.types.Inet
import org.springframework.stereotype.Repository
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

data class EmailLoginCodeRow(
    val id: UUID,
    val codeHash: String,
    val expiresAt: Instant,
    val attempts: Int,
    val consumedAt: Instant?,
    val createdAt: Instant,
)

@Repository
class EmailLoginCodeRepository(
    private val dsl: DSLContext,
) {
    /** The newest code for [email]; only that one can be used. Locked when [forUpdate]. */
    fun latest(
        email: String,
        forUpdate: Boolean = false,
    ): EmailLoginCodeRow? {
        val c = EMAIL_LOGIN_CODE
        val query =
            dsl
                .select(c.ID, c.CODE_HASH, c.EXPIRES_AT, c.ATTEMPTS, c.CONSUMED_AT, c.CREATED_AT)
                .from(c)
                .where(c.EMAIL.eq(email))
                .orderBy(c.CREATED_AT.desc(), c.ID.desc())
                .limit(1)
        val record = (if (forUpdate) query.forUpdate() else query).fetchOne() ?: return null
        return EmailLoginCodeRow(
            id = record.required(c.ID),
            codeHash = record.required(c.CODE_HASH),
            expiresAt = record.required(c.EXPIRES_AT),
            attempts = record.required(c.ATTEMPTS),
            consumedAt = record[c.CONSUMED_AT],
            createdAt = record.required(c.CREATED_AT),
        )
    }

    fun countCreatedSince(
        email: String,
        since: Instant,
    ): Int {
        val c = EMAIL_LOGIN_CODE
        return dsl.fetchCount(c, c.EMAIL.eq(email).and(c.CREATED_AT.gt(since)))
    }

    fun insert(
        email: String,
        codeHash: String,
        expiresAt: Instant,
        requestedIp: InetAddress?,
        now: Instant,
    ) {
        val c = EMAIL_LOGIN_CODE
        dsl
            .insertInto(c)
            .set(c.EMAIL, email)
            .set(c.CODE_HASH, codeHash)
            .set(c.EXPIRES_AT, expiresAt)
            .set(c.REQUESTED_IP, Inet.inetOrNull(requestedIp))
            .set(c.CREATED_AT, now)
            .execute()
    }

    fun incrementAttempts(id: UUID) {
        val c = EMAIL_LOGIN_CODE
        dsl
            .update(c)
            .set(c.ATTEMPTS, c.ATTEMPTS.plus(1))
            .where(c.ID.eq(id))
            .execute()
    }

    fun consume(
        id: UUID,
        now: Instant,
    ) {
        val c = EMAIL_LOGIN_CODE
        dsl
            .update(c)
            .set(c.CONSUMED_AT, now)
            .where(c.ID.eq(id))
            .execute()
    }

    fun deleteCreatedBefore(instant: Instant): Int {
        val c = EMAIL_LOGIN_CODE
        return dsl.deleteFrom(c).where(c.CREATED_AT.lt(instant)).execute()
    }
}

@Repository
class UserAccountRepository(
    private val dsl: DSLContext,
) {
    /**
     * The user with this verified [email], created on first sign-in, with an `EMAIL` identity.
     * Accounts are linked by verified email (ADR 0004).
     */
    fun findOrCreateByVerifiedEmail(
        email: String,
        now: Instant,
    ): UUID {
        val u = APP_USER
        val userId =
            dsl
                .insertInto(u)
                .set(u.EMAIL, email)
                .set(u.EMAIL_VERIFIED_AT, now)
                .set(u.CREATED_AT, now)
                .set(u.UPDATED_AT, now)
                .onConflict(u.EMAIL)
                .doUpdate()
                .set(u.EMAIL_VERIFIED_AT, coalesce(u.EMAIL_VERIFIED_AT, excluded(u.EMAIL_VERIFIED_AT)))
                .returning(u.ID)
                .fetchOne()
                ?.get(u.ID) ?: error("upsert returned no user")
        val i = USER_IDENTITY
        dsl
            .insertInto(i)
            .set(i.USER_ID, userId)
            .set(i.PROVIDER, "EMAIL")
            .set(i.SUBJECT, email.lowercase())
            .set(i.EMAIL, email)
            .set(i.CREATED_AT, now)
            .onConflictDoNothing()
            .execute()
        return userId
    }
}

data class RefreshTokenRow(
    val id: UUID,
    val userId: UUID,
    val familyId: UUID,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val revokedAt: Instant?,
    val deviceName: String?,
)

@Repository
class RefreshTokenRepository(
    private val dsl: DSLContext,
) {
    fun insert(
        userId: UUID,
        familyId: UUID,
        tokenHash: String,
        expiresAt: Instant,
        deviceName: String?,
        now: Instant,
    ): UUID {
        val t = REFRESH_TOKEN
        return dsl
            .insertInto(t)
            .set(t.USER_ID, userId)
            .set(t.FAMILY_ID, familyId)
            .set(t.TOKEN_HASH, tokenHash)
            .set(t.EXPIRES_AT, expiresAt)
            .set(t.DEVICE_NAME, deviceName)
            .set(t.CREATED_AT, now)
            .returning(t.ID)
            .fetchOne()
            ?.get(t.ID) ?: error("insert returned no id")
    }

    fun findByHashForUpdate(tokenHash: String): RefreshTokenRow? {
        val t = REFRESH_TOKEN
        val record =
            dsl
                .select(t.ID, t.USER_ID, t.FAMILY_ID, t.EXPIRES_AT, t.USED_AT, t.REVOKED_AT, t.DEVICE_NAME)
                .from(t)
                .where(t.TOKEN_HASH.eq(tokenHash))
                .forUpdate()
                .fetchOne() ?: return null
        return RefreshTokenRow(
            id = record.required(t.ID),
            userId = record.required(t.USER_ID),
            familyId = record.required(t.FAMILY_ID),
            expiresAt = record.required(t.EXPIRES_AT),
            usedAt = record[t.USED_AT],
            revokedAt = record[t.REVOKED_AT],
            deviceName = record[t.DEVICE_NAME],
        )
    }

    fun markUsed(
        id: UUID,
        replacedById: UUID,
        now: Instant,
    ) {
        val t = REFRESH_TOKEN
        dsl
            .update(t)
            .set(t.USED_AT, now)
            .set(t.REPLACED_BY_ID, replacedById)
            .where(t.ID.eq(id))
            .execute()
    }

    fun revokeFamily(
        familyId: UUID,
        now: Instant,
    ): Int {
        val t = REFRESH_TOKEN
        return dsl
            .update(t)
            .set(t.REVOKED_AT, now)
            .where(t.FAMILY_ID.eq(familyId), t.REVOKED_AT.isNull)
            .execute()
    }

    /** Expired tokens are useless, also for reuse detection: a reused expired token is just invalid. */
    fun deleteExpiredBefore(instant: Instant): Int {
        val t = REFRESH_TOKEN
        return dsl.deleteFrom(t).where(t.EXPIRES_AT.lt(instant)).execute()
    }
}

/** Columns declared `not null` are nullable in generated jOOQ code. */
private fun <T> Record.required(field: Field<T?>): T = get(field) ?: error("${field.name} is null")
