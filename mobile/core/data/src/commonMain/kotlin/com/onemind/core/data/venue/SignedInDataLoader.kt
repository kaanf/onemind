package com.onemind.core.data.venue

import com.onemind.core.data.profile.ProfileRepositoryImpl
import com.onemind.core.domain.session.SessionRepository
import com.onemind.core.domain.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Loads what every tab needs once signed in: the venue list (which also picks the default venue scope)
 * and the profile. Both loads run inside the session collector, so a sign-out cancels them before it
 * clears everything: an answer for one account can never land after the next one signs in. Each sign-in
 * also starts from an empty profile. Runs in the app scope for the app's lifetime.
 */
class SignedInDataLoader(
    private val sessions: SessionRepository,
    private val venues: VenueRepositoryImpl,
    private val profile: ProfileRepositoryImpl,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            // collectLatest cancels the previous block and waits for it before running the next one.
            sessions.state.collectLatest { state ->
                when (state) {
                    SessionState.SignedIn -> {
                        profile.clear()
                        coroutineScope {
                            launch { profile.refresh() }
                            venues.refresh()
                        }
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
