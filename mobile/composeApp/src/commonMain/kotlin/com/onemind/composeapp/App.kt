package com.onemind.composeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onemind.core.designsystem.theme.OneMindTheme

/** Root of the shared UI. Navigation, tab bar and Koin come with the app shell roadmap item. */
@Composable
fun App() {
    OneMindTheme {
        Box(modifier = Modifier.fillMaxSize().background(OneMindTheme.colors.background))
    }
}
