package com.onemind.core.domain.venue

import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the selected [VenueScope]. `null` until a venue is chosen (after sign-in and the venue list
 * load). The venue switcher sheet that changes it is not designed yet (P7 in `docs/OPEN_QUESTIONS.md`).
 */
interface VenueScopeRepository {
    val selected: StateFlow<VenueScope?>

    fun select(scope: VenueScope)

    /** Forgets the selection (sign-out). */
    fun clear()
}
