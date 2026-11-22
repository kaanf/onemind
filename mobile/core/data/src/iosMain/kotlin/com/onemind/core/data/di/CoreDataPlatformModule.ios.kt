package com.onemind.core.data.di

import com.onemind.core.data.session.IosSecureSessionStore
import com.onemind.core.data.session.SecureSessionStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

internal actual val coreDataPlatformModule =
    module {
        single<HttpClientEngine> { Darwin.create() }
        single<SecureSessionStore> { IosSecureSessionStore() }
    }
