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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight refresh after a `401` (ADR 0004). Callers pass the access token the server rejected:
 * if the stored session already has a newer one (another request refreshed first), that one is
 * returned without a second `POST /auth/refresh`, so a rotated refresh token is never sent twice
 * (which the backend would treat as theft and revoke the family). A `401` from the refresh signs
 * out; transport errors keep the session for the next try.
 */
class SessionRefresher(
    private val sessions: SessionManager,
) {
    private val mutex = Mutex()

    suspend fun refresh(
        client: HttpClient,
        rejectedAccessToken: String,
    ): StoredSession? =
        mutex.withLock {
            val current = sessions.current() ?: return@withLock null
            if (current.accessToken != rejectedAccessToken) return@withLock current

            val result =
                safeCall<AuthTokens> {
                    client.post(REFRESH_PATH) { setBody(RefreshTokenRequest(refreshToken = current.refreshToken)) }
                }
            when (result) {
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
