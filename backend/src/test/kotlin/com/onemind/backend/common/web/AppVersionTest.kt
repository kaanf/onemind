package com.onemind.backend.common.web

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `versions have one to four numeric parts and an optional suffix`() {
        listOf("1", "1.4", "1.4.2", "1.4.2.7", "1.4.2-beta.1", "1.4.2+87", " 1.4.2 ").forEach {
            assertTrue(AppVersion.parse(it) != null, it)
        }
        listOf("", "abc", "1.", ".1", "1..2", "1.2.3.4.5", "v1.2", "1.2.x", "-1.2", "1.2 beta", "1234567890").forEach {
            assertNull(AppVersion.parse(it), it)
        }
    }

    @Test
    fun `parts compare numerically, missing parts are zero and suffixes are ignored`() {
        assertTrue(v("1.10") > v("1.9"))
        assertTrue(v("2") > v("1.99.99"))
        assertTrue(v("1.4.0.1") > v("1.4"))
        assertEquals(v("1.4"), v("1.4.0"))
        assertEquals(v("1.4.0"), v("1.4.0-beta"))
        assertEquals(v("1.4.0+12"), v("1.4.0"))
        assertEquals("1.4.0", v("1.4.0-rc1").toString())
    }

    private fun v(value: String) = requireNotNull(AppVersion.parse(value))
}
