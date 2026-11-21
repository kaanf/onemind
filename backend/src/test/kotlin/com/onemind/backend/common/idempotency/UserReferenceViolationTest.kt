package com.onemind.backend.common.idempotency

import com.onemind.backend.common.idempotency.infra.persistence.violatesUserReference
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Only the record's app_user foreign key means "deleted user" (401); other integrity errors stay 500. */
class UserReferenceViolationTest {
    @Test
    fun `the app_user foreign key is a user reference violation`() {
        assertTrue(violatesUserReference(wrapped("23503", "idempotency_record_user_id_fkey")))
    }

    @Test
    fun `other foreign keys, check and unique violations are not`() {
        assertFalse(violatesUserReference(wrapped("23503", "idempotency_record_other_fkey")))
        assertFalse(violatesUserReference(wrapped("23514", "idempotency_record_check")))
        assertFalse(violatesUserReference(wrapped("23505", "idempotency_record_pkey")))
        assertFalse(violatesUserReference(DataIntegrityViolationException("no SQL cause")))
    }

    @Test
    fun `the constraint is read from the server's fields, not the message language`() {
        assertTrue(violatesUserReference(wrapped("23503", "idempotency_record_user_id_fkey", message = "viola la restricción")))
        assertFalse(violatesUserReference(wrapped("23503", "other_fkey", message = "idempotency_record_user_id_fkey")))
    }

    private fun wrapped(
        sqlState: String,
        constraint: String,
        message: String = "insert or update on table violates constraint",
    ) = DataIntegrityViolationException(
        "translated",
        // Fields as the server sends them: S severity, C SQLSTATE, M message, n constraint.
        PSQLException(ServerErrorMessage("SERROR\u0000C$sqlState\u0000M$message\u0000n$constraint\u0000")),
    )
}
