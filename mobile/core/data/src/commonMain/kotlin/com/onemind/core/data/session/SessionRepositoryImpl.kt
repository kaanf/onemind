package com.onemind.core.data.session

import com.onemind.core.api.model.RefreshTokenRequest
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.StateFlow

class SessionRepositoryImpl(
    private val sessions: SessionManager,
    private val client: HttpClient,
) : SessionRepository {
    override val state: StateFlow<SessionState> = sessions.state

    /** Logout is always `204` on the server, so its result is ignored: offline sign-out still forgets the tokens. */
    override suspend fun signOut() {
        val session = sessions.current() ?: return
        safeCall<Unit> { client.post("auth/logout") { setBody(RefreshTokenRequest(refreshToken = session.refreshToken)) } }
        sessions.clear()
    }
}
