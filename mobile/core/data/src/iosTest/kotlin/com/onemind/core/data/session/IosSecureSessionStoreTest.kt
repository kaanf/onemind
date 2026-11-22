package com.onemind.core.data.session

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs against the simulator's real Keychain, in its own service so it never touches the app's item. */
class IosSecureSessionStoreTest {
    private val service = "com.onemind.app.session.tests"
    private val defaults = NSUserDefaults(suiteName = "com.onemind.app.session.tests")
    private val store = IosSecureSessionStore(serviceName = service, defaults = defaults)

    @AfterTest
    fun cleanUp() =
        runTest {
            store.clear()
            defaults.removeObjectForKey("$service.installed")
        }

    @Test
    fun aLeftoverItemFromAPreviousInstallIsRemoved() =
        runTest {
            store.write("from the last install")
            // Simulate a reinstall: NSUserDefaults is gone, the Keychain item is not.
            defaults.removeObjectForKey("$service.installed")
            val reinstalled = IosSecureSessionStore(serviceName = service, defaults = defaults)

            assertNull(reinstalled.read())
        }

    @Test
    fun theInstallCheckRunsOnlyOnce() =
        runTest {
            store.write("kept")
            val nextLaunch = IosSecureSessionStore(serviceName = service, defaults = defaults)

            assertEquals("kept", nextLaunch.read())
        }

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
