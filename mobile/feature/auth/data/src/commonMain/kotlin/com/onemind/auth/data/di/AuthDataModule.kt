package com.onemind.auth.data.di

import com.onemind.auth.data.AuthRepositoryImpl
import com.onemind.auth.domain.AuthRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule =
    module {
        singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    }
