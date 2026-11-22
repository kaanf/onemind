package com.onemind.core.data.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiConfigTest {
    private val config = ApiConfig.fromBuild()

    @Test
    fun environmentIsDevOrProd() {
        assertTrue(config.environment in setOf("dev", "prod"), config.environment)
    }

    @Test
    fun baseUrlIsAHostWithoutTheApiPath() {
        assertTrue(config.baseUrl.startsWith("http://") || config.baseUrl.startsWith("https://"), config.baseUrl)
        assertFalse(config.baseUrl.endsWith("/"), config.baseUrl)
        assertFalse(config.baseUrl.contains("/api/"), config.baseUrl)
    }

    @Test
    fun prodUsesHttps() {
        if (config.environment == "prod") {
            assertEquals("https", config.baseUrl.substringBefore("://"))
        }
    }
}
