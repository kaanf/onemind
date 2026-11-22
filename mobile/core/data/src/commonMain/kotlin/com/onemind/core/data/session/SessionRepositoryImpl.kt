package com.onemind.core.data.session

import com.onemind.core.api.model.RefreshTokenRequest
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class SessionRepositoryImpl(
    private val sessions: SessionManager,
    private val client: HttpClient,
    private val scope: CoroutineScope,
) : SessionRepository {
    override val state: StateFlow<SessionState> = sessions.state

    /**
     * Forgets the tokens first (not cancellable), then revokes the sign-in on the server in the app
     * scope, best effort: logout is always `204`, and neither a cancelled caller nor being offline
     * may leave tokens on the device.
     */
    override suspend fun signOut() {
        val session =
            withContext(NonCancellable) {
                sessions.current()?.also { sessions.clear() }
            } ?: return
        scope
            .async { safeCall<Unit> { client.post("auth/logout") { setBody(RefreshTokenRequest(refreshToken = session.refreshToken)) } } }
            .await()
    }
}
