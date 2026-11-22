package com.onemind.core.data.session

import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        scope.launch { current() }
    }

    suspend fun current(): StoredSession? =
        mutex.withLock {
            ensureLoaded()
            session
        }

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

    private suspend fun ensureLoaded() {
        if (loaded) return
        session = readStored()
        loaded = true
        _state.value = if (session == null) SessionState.SignedOut else SessionState.SignedIn
    }

    private suspend fun readStored(): StoredSession? {
        val raw =
            try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null
        val decoded =
            try {
                json.decodeFromString(StoredSession.serializer(), raw).takeIf { it.version == StoredSession.FORMAT_VERSION }
            } catch (_: Exception) {
                null
            }
        if (decoded == null) store.clear()
        return decoded
    }

    private fun set(newSession: StoredSession?) {
        session = newSession
        _state.value = if (newSession == null) SessionState.SignedOut else SessionState.SignedIn
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
