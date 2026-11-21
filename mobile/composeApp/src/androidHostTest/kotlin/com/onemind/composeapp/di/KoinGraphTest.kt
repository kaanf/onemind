package com.onemind.composeapp.di

import com.onemind.core.domain.venue.VenueScopeRepository
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertSame

class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDefinitionCanBeResolved() {
        module { includes(appModules) }.verify()
    }

    @Test
    fun venueScopeIsOneSharedInstance() {
        val koin = koinApplication { modules(appModules) }.koin

        assertSame(koin.get<VenueScopeRepository>(), koin.get<VenueScopeRepository>())
    }
}
