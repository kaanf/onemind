package com.onemind.core.data.session

import com.onemind.core.api.model.AuthTokens
import com.onemind.core.api.model.RefreshTokenRequest
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight refresh after a `401` (ADR 0004). Callers pass the access token the server rejected:
 * if the stored session already has a newer one (another request refreshed first), that one is
 * returned without a second `POST /auth/refresh`, so a rotated refresh token is never sent twice
 * (which the backend would treat as theft and revoke the family).
 *
 * The refresh itself runs once, in [scope] (the app scope), and callers only await it. A caller that
 * is cancelled mid-refresh (screen closed) therefore can't drop the rotated tokens after the server
 * has already consumed the old refresh token. A `401` from the refresh signs out; transport errors
 * keep the session for the next try.
 */
class SessionRefresher(
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<StoredSession?>? = null

    suspend fun refresh(
        client: HttpClient,
        rejectedAccessToken: String,
    ): StoredSession? {
        val refresh =
            mutex.withLock {
                val current = sessions.current() ?: return null
                if (current.accessToken != rejectedAccessToken) return current
                inFlight?.takeIf { it.isActive } ?: scope.async { refreshNow(client, current) }.also { inFlight = it }
            }
        return refresh.await()
    }

    private suspend fun refreshNow(
        client: HttpClient,
        current: StoredSession,
    ): StoredSession? {
        val result =
            safeCall<AuthTokens> {
                client.post(REFRESH_PATH) { setBody(RefreshTokenRequest(refreshToken = current.refreshToken)) }
            }
        return when (result) {
            is Result.Success -> {
                val refreshed = StoredSession.from(result.data)
                if (sessions.replace(expected = current, newSession = refreshed)) refreshed else sessions.current()
            }

            is Result.Failure -> {
                val error = result.error
                if (error is DataError.Api && error.status == HttpStatusCode.Unauthorized.value) {
                    sessions.replace(expected = current, newSession = null)
                }
                null
            }
        }
    }

    private companion object {
        const val REFRESH_PATH = "auth/refresh"
    }
}
