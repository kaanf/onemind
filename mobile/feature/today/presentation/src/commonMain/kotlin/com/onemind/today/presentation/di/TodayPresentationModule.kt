package com.onemind.today.presentation.di

import com.onemind.today.presentation.today.TodayPollInterval
import com.onemind.today.presentation.today.TodayViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** ViewModels of the Today tab. */
val todayPresentationModule =
    module {
        single { TodayPollInterval.Default }
        viewModelOf(::TodayViewModel)
    }
