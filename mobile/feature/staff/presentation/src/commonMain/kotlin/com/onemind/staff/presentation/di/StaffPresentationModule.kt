package com.onemind.staff.presentation.di

import com.onemind.staff.presentation.staff.StaffViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModels of the Staff tab. */
val staffPresentationModule =
    module {
        viewModel { StaffViewModel(get(), get(), get()) }
    }
