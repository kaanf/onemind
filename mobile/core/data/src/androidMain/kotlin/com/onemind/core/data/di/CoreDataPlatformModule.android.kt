package com.onemind.core.data.di

import com.onemind.core.data.session.AndroidSecureSessionStore
import com.onemind.core.data.session.SecureSessionStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

internal actual val coreDataPlatformModule =
    module {
        single<HttpClientEngine> { OkHttp.create() }
        single<SecureSessionStore> { AndroidSecureSessionStore(androidContext()) }
    }
