package com.onemind.core.data.di

import com.onemind.core.data.cache.DataStoreScreenCache
import com.onemind.core.data.cache.ScreenCache
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
        // noBackupFilesDir: cached revenue figures never leave the device through a backup.
        single<ScreenCache> {
            val file = androidContext().noBackupFilesDir.resolve(DataStoreScreenCache.FILE_NAME)
            DataStoreScreenCache(DataStoreScreenCache.createDataStore(file.absolutePath), clock = get())
        }
    }
