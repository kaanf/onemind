package com.onemind.core.data.session

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs against the simulator's real Keychain, in its own service so it never touches the app's item. */
class IosSecureSessionStoreTest {
    private val store = IosSecureSessionStore(serviceName = "com.onemind.app.session.tests")

    @AfterTest
    fun cleanUp() = runTest { store.clear() }

    @Test
    fun writeReadOverwriteClear() =
        runTest {
            store.clear()
            assertNull(store.read())

            store.write("first")
            assertEquals("first", store.read())

            store.write("second ✓")
            assertEquals("second ✓", store.read())

            store.clear()
            assertNull(store.read())
        }

    @Test
    fun clearWithoutAnItemIsFine() =
        runTest {
            store.clear()
            store.clear()
            assertNull(store.read())
        }
}
