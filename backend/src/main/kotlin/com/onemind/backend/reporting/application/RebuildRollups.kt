package com.onemind.backend.reporting.application

import com.onemind.backend.pos.api.PosReadModel
import com.onemind.backend.reporting.domain.RollupVersion
import com.onemind.backend.reporting.infra.persistence.RollupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Brings stored rollups up to date: every business day with bills whose rollups are missing (an event
 * that never arrived) or come from an older calculation ([RollupVersion]) is recomputed, at most
 * [DAYS_PER_RUN] per run, newest first (after a version bump the current week and month come back
 * first; their version-dependent figures are hidden until then). Resumable and idempotent: a day done is
 * current and isn't picked again.
 */
@Service
class RebuildRollups(
    private val pos: PosReadModel,
    private val rollups: RollupRepository,
    private val refresh: RefreshRollups,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Recomputes up to [budget] stale days over all venues; returns how many it did. */
    fun run(budget: Int = DAYS_PER_RUN): Int {
        var done = 0
        for (venueId in pos.syncedVenueIds()) {
            if (done >= budget) break
            val stale = rollups.staleDays(venueId, pos.businessDays(venueId), RollupVersion.CURRENT).take(budget - done)
            if (stale.isEmpty()) continue
            refresh.refresh(venueId, stale)
            done += stale.size
            log.info("Rollups rebuilt: venueId={}, days={}", venueId, stale.size)
        }
        return done
    }

    companion object {
        const val DAYS_PER_RUN = 60
    }
}
