package com.onemind.today.data.di

import com.onemind.today.data.TodayRepositoryImpl
import com.onemind.today.domain.TodayRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val todayDataModule =
    module {
        singleOf(::TodayRepositoryImpl) bind TodayRepository::class
    }
