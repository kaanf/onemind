package com.onemind.core.data.di

import com.onemind.core.data.venue.InMemoryVenueScopeRepository
import com.onemind.core.domain.time.systemClock
import com.onemind.core.domain.venue.VenueScopeRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val coreDataModule =
    module {
        single<Clock> { systemClock }
        singleOf(::InMemoryVenueScopeRepository) bind VenueScopeRepository::class
    }
