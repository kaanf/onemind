package com.onemind.core.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DataStoreScreenCacheTest {
    private class StepClock : Clock {
        var now = Instant.parse("2026-11-22T20:40:00Z")

        override fun now() = now.also { now += 1.seconds }
    }

    /** A fresh file per test: DataStore allows one instance per file in a process. */
    private fun newFile() =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("screen-cache-${Random.nextLong().toULong()}.preferences_pb").toString()

    @Test
    fun writesAndReadsAnEntry() =
        runTest {
            val clock = StepClock()
            val cache = DataStoreScreenCache(DataStoreScreenCache.createDataStore(newFile()), clock)

            assertNull(cache.read("dashboard/today?venueIds=a"))
            cache.write("dashboard/today?venueIds=a", """{"status":"READY"}""", "\"v1\"", cache.generation)

            val entry = cache.read("dashboard/today?venueIds=a")
            assertEquals(CachedScreen("""{"status":"READY"}""", "\"v1\"", Instant.parse("2026-11-22T20:40:00Z")), entry)
        }

    @Test
    fun keepsOnlyTheNewestEntries() =
        runTest {
            val cache = DataStoreScreenCache(DataStoreScreenCache.createDataStore(newFile()), StepClock(), maxEntries = 2)

            cache.write("a", "1", null, cache.generation)
            cache.write("b", "2", null, cache.generation)
            cache.write("c", "3", null, cache.generation)

            assertNull(cache.read("a"))
            assertEquals("2", cache.read("b")?.body)
            assertEquals("3", cache.read("c")?.body)
        }

    @Test
    fun clearEmptiesTheCache() =
        runTest {
            val cache = DataStoreScreenCache(DataStoreScreenCache.createDataStore(newFile()), StepClock())
            cache.write("a", "1", "\"v1\"", cache.generation)

            cache.clear()

            assertNull(cache.read("a"))
        }

    @Test
    fun anUnreadableEntryIsDropped() =
        runTest {
            val store = DataStoreScreenCache.createDataStore(newFile())
            val cache = DataStoreScreenCache(store, StepClock())
            store.edit { it[stringPreferencesKey("screen:a")] = "not json" }

            assertNull(cache.read("a"))
            assertNull(cache.read("a"))
        }

    @Test
    fun aWriteStartedBeforeAClearIsDropped() =
        runTest {
            val cache = DataStoreScreenCache(DataStoreScreenCache.createDataStore(newFile()), StepClock())
            // A poll of the signed-out account takes the generation, then the sign-out clears.
            val before = cache.generation
            cache.clear()

            cache.write("a", "account A", "\"v1\"", before)

            assertNull(cache.read("a"))
            cache.write("a", "account B", "\"v2\"", cache.generation)
            assertEquals("account B", cache.read("a")?.body)
        }

    @Test
    fun aCorruptFileIsReplacedByAnEmptyCache() =
        runTest {
            val file = newFile()
            FileSystem.SYSTEM.write(file.toPath()) { writeUtf8("not a preferences file") }
            val cache = DataStoreScreenCache(DataStoreScreenCache.createDataStore(file), StepClock())

            assertNull(cache.read("a"))
            cache.write("a", "1", null, cache.generation)
            assertEquals("1", cache.read("a")?.body)
        }

    @Test
    fun aStorageErrorReadsAsNothingCachedAndNeverThrows() =
        runTest {
            val cache = DataStoreScreenCache(FailingDataStore(), StepClock())

            assertNull(cache.read("a"))
            cache.write("a", "1", null, cache.generation)
            cache.clear()
        }

    /** A DataStore whose file can't be read or written (a full disk, a broken file system). */
    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("disk") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
    }

    @Test
    fun theKeyHasItsParametersInAStableOrder() {
        assertEquals("dashboard/today", screenCacheKey("dashboard/today"))
        assertEquals(
            "dashboard/sales?period=WEEK&venueIds=a,b",
            screenCacheKey(
                "dashboard/sales",
                mapOf(
                    "venueIds" to "a,b",
                    "period" to "WEEK",
                ),
            ),
        )
    }
}
