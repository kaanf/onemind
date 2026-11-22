package com.onemind.core.data.di

import org.koin.core.module.Module

/** Binds the platform's `HttpClientEngine` and `SecureSessionStore`. */
internal expect val coreDataPlatformModule: Module
