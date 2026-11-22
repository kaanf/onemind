package com.onemind.core.data.venue

import com.onemind.core.data.profile.ProfileRepositoryImpl
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Loads what every tab needs once signed in: the venue list (which also picks the default venue scope)
 * and the profile. Signing out forgets them, so the next account starts clean. Runs in the app scope
 * for the app's lifetime.
 */
class SignedInDataLoader(
    private val sessions: SessionRepository,
    private val venues: VenueRepositoryImpl,
    private val profile: ProfileRepositoryImpl,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            sessions.state.collectLatest { state ->
                when (state) {
                    SessionState.SignedIn -> {
                        scope.launch { profile.refresh() }
                        venues.refresh()
                    }

                    SessionState.SignedOut -> {
                        venues.clear()
                        profile.clear()
                    }

                    SessionState.Unknown -> {
                        Unit
                    }
                }
            }
        }
    }
}
