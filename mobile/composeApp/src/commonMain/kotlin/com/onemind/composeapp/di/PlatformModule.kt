package com.onemind.composeapp.di

import org.koin.core.module.Module

/** Platform bindings (secure storage, DataStore file location, …); each target provides its own. */
expect val platformModule: Module
