package com.onemind.staff.data.di

import com.onemind.staff.data.StaffRepositoryImpl
import com.onemind.staff.domain.StaffRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val staffDataModule =
    module {
        singleOf(::StaffRepositoryImpl) bind StaffRepository::class
    }
