package com.onemind.app

import android.app.Application
import com.onemind.composeapp.di.initKoin
import org.koin.android.ext.koin.androidContext

class OneMindApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OneMindApplication)
        }
    }
}
