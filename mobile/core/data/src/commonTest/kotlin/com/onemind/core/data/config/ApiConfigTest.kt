package com.onemind.core.data.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiConfigTest {
    private val config = ApiConfig.fromBuild()

    @Test
    fun baseUrlIsAHostWithoutAPath() {
        assertTrue(Regex("^https?://[^/\\s]+$").matches(config.baseUrl), config.baseUrl)
    }

    @Test
    fun prodUsesHttps() {
        if (config.environment == AppEnvironment.PROD) {
            assertEquals("https", config.baseUrl.substringBefore("://"))
        }
    }

    @Test
    fun devNeverUsesTheProdHost() {
        if (config.environment == AppEnvironment.DEV) {
            assertFalse(config.baseUrl.contains("onemind.app"), config.baseUrl)
        }
    }

    @Test
    fun appVersionIsNumeric() {
        assertTrue(Regex("^\\d+(\\.\\d+)*$").matches(config.appVersion), config.appVersion)
    }

    @Test
    fun environmentKeysRoundTrip() {
        AppEnvironment.entries.forEach { assertEquals(it, AppEnvironment.fromKey(it.key)) }
        assertFailsWith<IllegalStateException> { AppEnvironment.fromKey("staging") }
    }
}
