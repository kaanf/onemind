package com.onemind.core.data.di

import org.koin.core.module.Module

/** Binds the platform's `HttpClientEngine`, `SecureSessionStore` and `ScreenCache` (its file outside backups). */
internal expect val coreDataPlatformModule: Module
