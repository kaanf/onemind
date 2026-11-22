package com.onemind.core.data.di

import com.onemind.core.data.config.ApiConfig
import com.onemind.core.data.network.HttpClientFactory
import com.onemind.core.data.profile.ProfileRepositoryImpl
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.SessionRefresher
import com.onemind.core.data.session.SessionRepositoryImpl
import com.onemind.core.data.update.InMemoryAppUpdateRepository
import com.onemind.core.data.venue.InMemoryVenueScopeRepository
import com.onemind.core.data.venue.SignedInDataLoader
import com.onemind.core.data.venue.VenueRepositoryImpl
import com.onemind.core.domain.profile.ProfileRepository
import com.onemind.core.domain.session.AppUpdateRepository
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.venue.VenueRepository
import com.onemind.core.domain.venue.VenueScopeRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/** Qualifier of the app-lifetime [CoroutineScope] (work that must outlive a screen). */
val AppScope = named("appScope")

val coreDataModule =
    module {
        includes(coreDataPlatformModule)

        // The clock seam: the only place that reads the system clock (`:core:konsist` guards this).
        // Everything else injects Clock, so tests can pass a fixed one.
        single<Clock> { Clock.System }
        single(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { ApiConfig.fromBuild() }
        singleOf(::InMemoryVenueScopeRepository) bind VenueScopeRepository::class

        single { SessionManager(store = get(), scope = get(AppScope)) }
        single { SessionRefresher(sessions = get(), scope = get(AppScope)) }
        single { InMemoryAppUpdateRepository() } bind AppUpdateRepository::class
        single<HttpClient> { HttpClientFactory(get(), get(), get(), get()).create(get()) }
        single { SessionRepositoryImpl(sessions = get(), client = get(), scope = get(AppScope)) } bind SessionRepository::class
        single { VenueRepositoryImpl(client = get(), venueScope = get()) } bind VenueRepository::class
        single { ProfileRepositoryImpl(client = get()) } bind ProfileRepository::class
        // Started once by initKoin (composeApp): loads the venues and the profile on sign-in, forgets them on sign-out.
        single { SignedInDataLoader(sessions = get(), venues = get(), profile = get(), scope = get(AppScope)) }
    }
