package com.onemind.core.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [ScreenCache] in a Preferences DataStore file (ADR 0006): one string entry per screen key, at most
 * [maxEntries] (the oldest go first). The file is outside device backups (platform DI picks the
 * directory). The cache is never worth a crash: a corrupt file is replaced by an empty one
 * ([createDataStore]), an entry the app can't read is dropped, and a storage error reads as "nothing
 * cached" and skips the write.
 */
class DataStoreScreenCache(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val maxEntries: Int = MAX_ENTRIES,
) : ScreenCache {
    @Serializable
    private data class Entry(
        val body: String,
        val etag: String? = null,
        val savedAtMillis: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Writes and clears take turns, so a write checks [generation] against the latest clear. */
    private val mutex = Mutex()

    @Volatile
    override var generation: Long = 0
        private set

    override suspend fun read(key: String): CachedScreen? =
        try {
            val raw = dataStore.data.first()[prefKey(key)]
            val entry = raw?.let(::decode)
            if (raw != null && entry == null) dataStore.edit { it.remove(prefKey(key)) }
            entry?.let { CachedScreen(it.body, it.etag, Instant.fromEpochMilliseconds(it.savedAtMillis)) }
        } catch (_: IOException) {
            null
        }

    override suspend fun write(
        key: String,
        body: String,
        etag: String?,
        generation: Long,
    ) {
        mutex.withLock {
            // Cleared since the request started (sign-out): that account's answer is not kept.
            if (generation != this.generation) return
            val entry = Entry(body, etag, clock.now().toEpochMilliseconds())
            try {
                dataStore.edit { prefs ->
                    prefs[prefKey(key)] = json.encodeToString(Entry.serializer(), entry)
                    trim(prefs)
                }
            } catch (_: IOException) {
                // Not cached this time; the screen still has the fresh answer.
            }
        }
    }

    override suspend fun clear() {
        // Sign-out: finish even if the caller is cancelled, so no figures of the account stay behind.
        withContext(NonCancellable) {
            mutex.withLock {
                generation++
                try {
                    dataStore.edit { it.clear() }
                } catch (_: IOException) {
                    // Nothing more to do here; the next successful write overwrites the file.
                }
            }
        }
    }

    private fun trim(prefs: MutablePreferences) {
        val cached =
            prefs
                .asMap()
                .filterKeys { it.name.startsWith(PREFIX) }
                .mapValues { (_, value) -> (value as? String)?.let(::decode)?.savedAtMillis ?: Long.MIN_VALUE }
        if (cached.size > maxEntries) {
            cached.entries
                .sortedBy { it.value }
                .take(cached.size - maxEntries)
                .forEach { prefs.remove(it.key) }
        }
    }

    private fun decode(raw: String): Entry? =
        try {
            json.decodeFromString(Entry.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun prefKey(key: String) = stringPreferencesKey(PREFIX + key)

    companion object {
        const val FILE_NAME = "screen_cache.preferences_pb"
        private const val PREFIX = "screen:"
        private const val MAX_ENTRIES = 20

        /**
         * The one DataStore of the cache file at [path] (DataStore allows one instance per file). A
         * corrupt file is replaced by an empty cache instead of failing every read.
         */
        fun createDataStore(path: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { path.toPath() },
            )
    }
}
