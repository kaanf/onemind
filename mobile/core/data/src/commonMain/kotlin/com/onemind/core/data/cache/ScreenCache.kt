package com.onemind.core.data.cache

import kotlin.time.Instant

/**
 * The read cache of ADR 0006: the last successful answer of each screen, so a screen can show its
 * figures at once on launch or offline, always with their age. It holds API bodies as sent (raw JSON),
 * never edited locally; the server stays the truth. Account data: cleared on sign-out.
 */
interface ScreenCache {
    suspend fun read(key: String): CachedScreen?

    suspend fun write(
        key: String,
        body: String,
        etag: String?,
    )

    suspend fun clear()
}

/** One cached answer: the JSON body, its `ETag` and when the app received it. */
data class CachedScreen(
    val body: String,
    val etag: String?,
    val savedAt: Instant,
)

/** The cache key of a screen call: the path and its parameters in a stable order (`dashboard/today?venueIds=a,b`). */
fun screenCacheKey(
    path: String,
    params: Map<String, String> = emptyMap(),
): String =
    if (params.isEmpty()) path else path + params.entries.sortedBy { it.key }.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
