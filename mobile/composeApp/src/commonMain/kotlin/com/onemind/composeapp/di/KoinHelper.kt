package com.onemind.composeapp.di

import com.onemind.auth.data.di.authDataModule
import com.onemind.auth.presentation.di.authPresentationModule
import com.onemind.core.data.di.coreDataModule
import com.onemind.core.data.venue.SignedInDataLoader
import com.onemind.sales.data.di.salesDataModule
import com.onemind.sales.presentation.di.salesPresentationModule
import com.onemind.staff.presentation.di.staffPresentationModule
import com.onemind.today.data.di.todayDataModule
import com.onemind.today.presentation.di.todayPresentationModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Every Koin module of the app; the graph test verifies this list. */
val appModules: List<Module>
    get() =
        listOf(
            platformModule,
            coreDataModule,
            authDataModule,
            authPresentationModule,
            todayDataModule,
            todayPresentationModule,
            salesDataModule,
            salesPresentationModule,
            staffPresentationModule,
        )

/**
 * Called once by each host: `OneMindApplication` on Android, `iOSApp.init` on iOS. Also starts the
 * app-lifetime loader of the signed-in user's venues and profile.
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    val koin =
        startKoin {
            config?.invoke(this)
            modules(appModules)
        }.koin
    koin.get<SignedInDataLoader>().start()
}
