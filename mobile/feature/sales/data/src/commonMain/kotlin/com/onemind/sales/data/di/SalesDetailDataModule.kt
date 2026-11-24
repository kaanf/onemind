package com.onemind.sales.data.di

import com.onemind.sales.data.losses.LossesRepositoryImpl
import com.onemind.sales.domain.losses.LossesRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Repositories of the Sales detail screens (02a Products by hour, 02c Discounts & losses). */
val salesDetailDataModule =
    module {
        singleOf(::LossesRepositoryImpl) bind LossesRepository::class
    }
