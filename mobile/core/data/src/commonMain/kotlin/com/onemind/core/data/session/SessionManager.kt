package com.onemind.core.data.session

import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The single owner of this device's tokens: an in-memory copy of [SecureSessionStore], loaded once
 * (started in [scope] at creation so the app gate leaves [SessionState.Unknown] quickly). All reads
 * and writes go through one mutex, so a refresh and a sign-out can't interleave.
 *
 * A store that throws on read (iOS Keychain before the first unlock, an I/O error) is transient: the
 * state stays [SessionState.Unknown], requests meanwhile go out without a token, and the load is
 * retried with backoff and on every access, so it never turns into a sign-out for the process.
 * Only a value that reads but can't be decoded is discarded.
 */
class SessionManager(
    private val store: SecureSessionStore,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var session: StoredSession? = null
    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        scope.launch {
            var delayMs = FIRST_RETRY_MS
            while (true) {
                try {
                    current()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Never let the app scope crash the process; the next access retries too.
                }
                if (loaded) break
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        }
    }

    suspend fun current(): StoredSession? =
        mutex.withLock {
            ensureLoaded()
            session
        }

    /**
     * Stores [newSession]: a sign-in or a token refresh of the same user. Replacing one account with
     * another must go through sign-out first ([clear], so the state passes `SignedOut`): the
     * account-scoped data (venues, venue scope, profile; `SignedInDataLoader`) is cleared only on
     * `SignedOut`. A `StoredSession` has no user id, so an in-app account switch that saved straight
     * over a signed-in session would keep the previous account's data on screen.
     */
    suspend fun save(newSession: StoredSession) {
        mutex.withLock {
            ensureLoaded()
            store.write(json.encodeToString(StoredSession.serializer(), newSession))
            set(newSession)
        }
    }

    /** Replaces the session only if it is still [expected] (so a stale refresh can't undo a sign-out). */
    suspend fun replace(
        expected: StoredSession,
        newSession: StoredSession?,
    ): Boolean =
        mutex.withLock {
            ensureLoaded()
            if (session != expected) return@withLock false
            if (newSession == null) store.clear() else store.write(json.encodeToString(StoredSession.serializer(), newSession))
            set(newSession)
            true
        }

    suspend fun clear() {
        mutex.withLock {
            ensureLoaded()
            set(null)
            store.clear()
        }
    }

    /** Loads once; a throwing store leaves [loaded] false (state [SessionState.Unknown]) so the next access retries. */
    private suspend fun ensureLoaded() {
        if (loaded) return
        val raw =
            try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
        session = raw?.let { decodeOrDiscard(it) }
        loaded = true
        _state.value = if (session == null) SessionState.SignedOut else SessionState.SignedIn
    }

    private suspend fun decodeOrDiscard(raw: String): StoredSession? {
        val decoded =
            try {
                json.decodeFromString(StoredSession.serializer(), raw).takeIf { it.version == StoredSession.FORMAT_VERSION }
            } catch (_: Exception) {
                null
            }
        if (decoded == null) {
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Unreadable either way; a later save overwrites it.
            }
        }
        return decoded
    }

    private fun set(newSession: StoredSession?) {
        session = newSession
        _state.value = if (newSession == null) SessionState.SignedOut else SessionState.SignedIn
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val FIRST_RETRY_MS = 500L
        const val MAX_RETRY_MS = 30_000L
    }
}
