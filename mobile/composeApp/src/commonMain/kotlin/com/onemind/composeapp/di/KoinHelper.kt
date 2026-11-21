package com.onemind.composeapp.di

import com.onemind.core.data.di.coreDataModule
import com.onemind.sales.presentation.di.salesPresentationModule
import com.onemind.staff.presentation.di.staffPresentationModule
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
            todayPresentationModule,
            salesPresentationModule,
            staffPresentationModule,
        )

/** Called once by each host: `OneMindApplication` on Android, `iOSApp.init` on iOS. */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModules)
    }
}
