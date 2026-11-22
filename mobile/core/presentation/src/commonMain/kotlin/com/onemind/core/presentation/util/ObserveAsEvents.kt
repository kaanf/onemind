package com.onemind.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects one-off ViewModel events (navigation, snackbars) only while the screen is at least
 * STARTED, on `Dispatchers.Main.immediate` so an event is handled in the same frame it is sent.
 * Pair it with a `Channel` in the ViewModel: events sent while stopped wait instead of being lost.
 */
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    key1: Any? = null,
    key2: Any? = null,
    onEvent: suspend (T) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, key1, key2) {
        lifecycle.collectWhileStarted(flow, onEvent)
    }
}

/** The lifecycle part of [ObserveAsEvents], separate so it can be tested without Compose. */
internal suspend fun <T> Lifecycle.collectWhileStarted(
    flow: Flow<T>,
    onEvent: suspend (T) -> Unit,
) {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        withContext(Dispatchers.Main.immediate) {
            flow.collect(onEvent)
        }
    }
}
