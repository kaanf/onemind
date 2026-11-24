package com.onemind.core.presentation.util

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a screen waits for its first answer before it shows the figures saved on the device
 * (ADR 0006 §1). Until then it shows skeletons; the request keeps going either way.
 */
val CachedFallbackDelay: Duration = 5.seconds

/**
 * Runs [request] and returns its answer. When the answer takes longer than [delay], [onSlow] runs first
 * (the screen shows its cached figures), while the request keeps going (ADR 0006 §1).
 */
suspend fun <T : Any> awaitWithCachedFallback(
    request: suspend () -> T,
    delay: Duration = CachedFallbackDelay,
    onSlow: suspend () -> Unit,
): T =
    coroutineScope {
        val answer = async { request() }
        if (withTimeoutOrNull(delay) { answer.await() } == null) onSlow()
        answer.await()
    }
