package com.onemind.core.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [ScreenCache] in a Preferences DataStore file (ADR 0006): one string entry per screen key, at most
 * [maxEntries] (the oldest go first). The file is outside device backups (platform DI picks the
 * directory). An entry the app can't read any more is dropped as if it weren't there.
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

    override suspend fun read(key: String): CachedScreen? {
        val raw = dataStore.data.first()[prefKey(key)] ?: return null
        val entry = decode(raw)
        if (entry == null) {
            dataStore.edit { it.remove(prefKey(key)) }
            return null
        }
        return CachedScreen(entry.body, entry.etag, Instant.fromEpochMilliseconds(entry.savedAtMillis))
    }

    override suspend fun write(
        key: String,
        body: String,
        etag: String?,
    ) {
        val entry = Entry(body, etag, clock.now().toEpochMilliseconds())
        dataStore.edit { prefs ->
            prefs[prefKey(key)] = json.encodeToString(Entry.serializer(), entry)
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
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
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

        /** The one DataStore of the cache file at [path] (DataStore allows one instance per file). */
        fun createDataStore(path: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
    }
}
