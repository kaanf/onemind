package com.onemind.composeapp.di

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.config.AppEnvironment
import com.onemind.core.domain.venue.VenueScopeRepository
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDefinitionCanBeResolved() {
        // ApiConfig is built from BuildKonfig values, not injected; its enum field isn't a Koin definition.
        module { includes(appModules) }.verify(extraTypes = listOf(AppEnvironment::class))
    }

    @Test
    fun venueScopeIsOneSharedInstance() {
        val koin = koinApplication { modules(appModules) }.koin

        assertSame(koin.get<VenueScopeRepository>(), koin.get<VenueScopeRepository>())
    }

    @Test
    fun apiConfigComesFromTheBuild() {
        val koin = koinApplication { modules(appModules) }.koin

        assertEquals(ApiConfig.fromBuild(), koin.get<ApiConfig>())
    }
}
