package com.onemind.sales.data.di

import com.onemind.sales.data.SalesRepositoryImpl
import com.onemind.sales.domain.SalesRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val salesDataModule =
    module {
        singleOf(::SalesRepositoryImpl) bind SalesRepository::class
    }
