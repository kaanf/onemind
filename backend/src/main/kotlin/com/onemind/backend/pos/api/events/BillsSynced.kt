package com.onemind.backend.pos.api.events

import java.time.LocalDate
import java.util.UUID

/**
 * Bills of [venueId] were added or changed on [businessDays] (ADR 0013, events; STORYOUS.md sync
 * strategy). A small signal: listeners (rollups, stock usage) read the bills through the pos read API.
 * Published only when a sync page wrote something; delivery is at least once, so listeners are idempotent.
 */
data class BillsSynced(
    val venueId: UUID,
    val businessDays: List<LocalDate>,
)
