package com.onemind.core.data.session

import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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
    fun toStringNeverContainsTokens() {
        val text = session.toString()
        assertFalse(text.contains("access-secret"))
        assertFalse(text.contains("refresh-secret"))
    }
}
