package com.onemind.core.domain.session

import kotlinx.coroutines.flow.StateFlow

/** Whether this device has a session. Tokens never leave the data layer. */
sealed interface SessionState {
    /** Secure storage hasn't been read yet (app start). */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data object SignedIn : SessionState
}

interface SessionRepository {
    val state: StateFlow<SessionState>

    /** Revokes this sign-in on the server (best effort, `POST /auth/logout`) and forgets the tokens. */
    suspend fun signOut()
}
