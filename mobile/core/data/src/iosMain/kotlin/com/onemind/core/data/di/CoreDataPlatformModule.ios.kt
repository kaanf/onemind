package com.onemind.core.data.di

import com.onemind.core.data.cache.DataStoreScreenCache
import com.onemind.core.data.cache.ScreenCache
import com.onemind.core.data.session.IosSecureSessionStore
import com.onemind.core.data.session.SecureSessionStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual val coreDataPlatformModule =
    module {
        single<HttpClientEngine> { Darwin.create() }
        single<SecureSessionStore> { IosSecureSessionStore() }
        // Caches: not backed up to iCloud and not shown in Files, so cached revenue figures stay on the device.
        single<ScreenCache> {
            val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
            DataStoreScreenCache(DataStoreScreenCache.createDataStore("$caches/${DataStoreScreenCache.FILE_NAME}"), clock = get())
        }
    }
