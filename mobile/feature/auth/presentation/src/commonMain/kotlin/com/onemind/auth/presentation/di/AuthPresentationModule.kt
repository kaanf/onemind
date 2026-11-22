package com.onemind.auth.presentation.di

import com.onemind.auth.presentation.code.EmailCodeViewModel
import com.onemind.auth.presentation.signin.SignInViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule =
    module {
        viewModelOf(::SignInViewModel)
        viewModel { (email: String, resendAfterSeconds: Int) -> EmailCodeViewModel(email, resendAfterSeconds, get()) }
    }
