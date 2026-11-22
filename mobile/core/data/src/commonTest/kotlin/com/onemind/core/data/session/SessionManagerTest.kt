package com.onemind.core.data.session

import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val session =
        StoredSession(
            accessToken = "access-secret",
            refreshToken = "refresh-secret",
            accessTokenExpiresAt = Instant.parse("2026-11-22T10:15:00Z"),
            refreshTokenExpiresAt = Instant.parse("2026-12-22T10:00:00Z"),
        )

    @Test
    fun startsUnknownThenSignedOutWithAnEmptyStore() =
        runTest {
            val manager = SessionManager(FakeSecureSessionStore(), backgroundScope)
            assertNull(manager.current())
            assertEquals(SessionState.SignedOut, manager.state.value)
        }

    @Test
    fun savedSessionSurvivesARestart() =
        runTest {
            val store = FakeSecureSessionStore()
            SessionManager(store, backgroundScope).save(session)

            val restarted = SessionManager(store, backgroundScope)
            assertEquals(session, restarted.current())
            assertEquals(SessionState.SignedIn, restarted.state.value)
        }

    @Test
    fun unreadableStoredValueIsClearedAndSignsOut() =
        runTest {
            val store = FakeSecureSessionStore(value = "{not json")
            val manager = SessionManager(store, backgroundScope)

            assertNull(manager.current())
            assertNull(store.value)
            assertEquals(SessionState.SignedOut, manager.state.value)
        }

    @Test
    fun unknownFormatVersionIsDiscarded() =
        runTest {
            val store = FakeSecureSessionStore()
            SessionManager(store, backgroundScope).save(session.copy(version = 99))

            assertNull(SessionManager(store, backgroundScope).current())
        }

    @Test
    fun clearForgetsTheTokens() =
        runTest {
            val store = FakeSecureSessionStore()
            val manager = SessionManager(store, backgroundScope)
            manager.save(session)

            manager.clear()

            assertNull(manager.current())
            assertNull(store.value)
            assertEquals(SessionState.SignedOut, manager.state.value)
        }

    @Test
    fun replaceOnlyWhenStillExpected() =
        runTest {
            val manager = SessionManager(FakeSecureSessionStore(), backgroundScope)
            manager.save(session)
            val newer = session.copy(accessToken = "newer")

            assertTrue(manager.replace(expected = session, newSession = newer))
            assertFalse(manager.replace(expected = session, newSession = null))
            assertEquals(newer, manager.current())
        }

    @Test
    fun aThrowingStoreStaysUnknownAndRecoversOnTheNextAccess() =
        runTest {
            val store = FlakyStore(failReads = 2, value = encoded())
            val manager = SessionManager(store, backgroundScope)

            runCurrent()
            assertEquals(SessionState.Unknown, manager.state.value)
            assertNull(manager.current())
            assertEquals(SessionState.Unknown, manager.state.value)

            assertEquals(session, manager.current())
            assertEquals(SessionState.SignedIn, manager.state.value)
        }

    @Test
    fun theStartupLoadRetriesWithBackoff() =
        runTest {
            val store = FlakyStore(failReads = 2, value = encoded())
            val manager = SessionManager(store, backgroundScope)

            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(SessionState.SignedIn, manager.state.value)
            assertEquals(3, store.reads)
        }

    @Test
    fun aFailingClearOfAnUnreadableValueDoesNotCrash() =
        runTest {
            val store = FlakyStore(value = "{garbage", failClear = true)
            val manager = SessionManager(store, backgroundScope)

            assertNull(manager.current())
            assertEquals(SessionState.SignedOut, manager.state.value)
        }

    private suspend fun encoded(): String {
        val fake = FakeSecureSessionStore()
        SessionManager(fake, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job())).save(session)
        return fake.value!!
    }

    private class FlakyStore(
        var failReads: Int = 0,
        var value: String? = null,
        val failClear: Boolean = false,
    ) : SecureSessionStore {
        var reads = 0

        override suspend fun read(): String? {
            reads++
            if (failReads > 0) {
                failReads--
                error("keychain locked")
            }
            return value
        }

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            check(!failClear) { "clear failed" }
            value = null
        }
    }

    @Test
    fun toStringNeverContainsTokens() {
        val text = session.toString()
        assertFalse(text.contains("access-secret"))
        assertFalse(text.contains("refresh-secret"))
    }
}
