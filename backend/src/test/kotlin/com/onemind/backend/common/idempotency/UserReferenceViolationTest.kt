package com.onemind.backend.common.idempotency

import com.onemind.backend.common.idempotency.infra.violatesUserReference
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
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

    private fun wrapped(
        sqlState: String,
        constraint: String,
    ) = DataIntegrityViolationException(
        "translated",
        SQLException("ERROR: insert or update on table \"idempotency_record\" violates constraint \"$constraint\"", sqlState),
    )
}
