package com.onemind.core.data.di

import com.onemind.core.data.venue.InMemoryVenueScopeRepository
import com.onemind.core.domain.venue.VenueScopeRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val coreDataModule =
    module {
        // The clock seam: the only place that reads the system clock (`:core:konsist` guards this).
        // Everything else injects Clock, so tests can pass a fixed one.
        single<Clock> { Clock.System }
        singleOf(::InMemoryVenueScopeRepository) bind VenueScopeRepository::class
    }
