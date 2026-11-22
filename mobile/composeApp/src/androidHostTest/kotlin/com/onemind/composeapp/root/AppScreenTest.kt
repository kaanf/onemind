package com.onemind.composeapp.root

import com.onemind.core.domain.session.AppUpdateRequired
import com.onemind.core.domain.session.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals

class AppScreenTest {
    @Test
    fun sessionPicksTheScreen() {
        assertEquals(AppScreen.Loading, appScreen(SessionState.Unknown, null))
        assertEquals(AppScreen.Auth, appScreen(SessionState.SignedOut, null))
        assertEquals(AppScreen.Main, appScreen(SessionState.SignedIn, null))
    }

    @Test
    fun anUpdateCoversEverything() {
        val update = AppUpdateRequired(minimumVersion = "1.5.0")
        SessionState::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach { session ->
            assertEquals(AppScreen.Update("1.5.0"), appScreen(session, update))
        }
    }
}
