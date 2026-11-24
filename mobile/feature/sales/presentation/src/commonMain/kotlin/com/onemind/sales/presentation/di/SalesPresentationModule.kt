package com.onemind.sales.presentation.di

import com.onemind.sales.presentation.sales.SalesPollInterval
import com.onemind.sales.presentation.sales.SalesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** ViewModels of the Sales tab: 02 Sales. */
val salesPresentationModule =
    module {
        single { SalesPollInterval.Default }
        viewModelOf(::SalesViewModel)
    }
