package com.onemind.composeapp

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.onemind.composeapp.navigation.NavigationRoot
import com.onemind.core.designsystem.theme.OneMindTheme

/** Root of the shared UI. Koin is started by the host before this runs (`initKoin`). */
@Composable
fun App() {
    OneMindTheme {
        NavigationRoot(navController = rememberNavController())
    }
}
