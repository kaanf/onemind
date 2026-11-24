package com.onemind.sales.presentation.di

import com.onemind.sales.presentation.losses.LossesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** ViewModels of the Sales detail screens (02a Products by hour, 02c Discounts & losses). */
val salesDetailPresentationModule =
    module {
        viewModelOf(::LossesViewModel)
    }
