package com.onemind.backend.common.error

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DomainExceptionTest {
    class Probe(
        code: String,
    ) : ConflictException(code, "Detail.")

    @Test
    fun `codes must be SCREAMING_SNAKE`() {
        assertEquals("STOCK_ITEM_2_MISSING", Probe("STOCK_ITEM_2_MISSING").code)
        listOf("stock_missing", "STOCK-MISSING", "_STOCK", "STOCK_", "").forEach {
            assertFailsWith<IllegalArgumentException>(it) { Probe(it) }
        }
    }

    @Test
    fun `titles are derived from codes`() {
        assertEquals("Receipt already approved", Problems.titleOf("RECEIPT_ALREADY_APPROVED"))
    }
}
