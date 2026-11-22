package com.onemind.backend.pos.application

import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.persistence.PosConnectionRepository
import com.onemind.backend.pos.infra.persistence.PosConnectionRow
import com.onemind.backend.pos.infra.persistence.SyncJobRepository
import com.onemind.backend.pos.infra.persistence.SyncStepRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One sync run over every connection (`pos-sync` task, STORYOUS.md sync strategy): places → venues,
 * then menu and desks of each active venue when due; bills come with the bills sync. Each connection
 * gets a `sync_job` row with its steps; a failing connection or venue doesn't stop the others. Without
 * a configured [PosProvider] (no `onemind.pos.provider`, e.g. prod until the real client exists) the run
 * does nothing and says so once.
 */
@Service
class RunPosSync(
    private val connections: PosConnectionRepository,
    private val syncJobs: SyncJobRepository,
    private val syncPlaces: SyncPlaces,
    private val syncCatalog: SyncCatalog,
    private val providers: ObjectProvider<PosProvider>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val warnedNoProvider = AtomicBoolean()

    fun run() {
        val provider = providers.ifAvailable
        if (provider == null) {
            if (!warnedNoProvider.getAndSet(true)) log.warn("No POS provider configured (onemind.pos.provider): POS sync is skipped")
            return
        }
        connections.syncable().forEach { sync(it, provider) }
    }

    private fun sync(
        connection: PosConnectionRow,
        provider: PosProvider,
    ) {
        val job = syncJobs.start(connection.id, INCREMENTAL, clock.instant())
        val steps = mutableListOf<SyncStepRow>()
        try {
            val places = syncPlaces.run(connection, provider)
            steps += SyncStepRow(PLACES, SyncJobRepository.SUCCEEDED)
            places.venues.filter { it.active }.forEach { venue ->
                steps += syncCatalog.runIfDue(venue, PosPlaceRef(connection.externalMerchantId, venue.placeId), places.currency, provider)
            }
        } catch (e: RuntimeException) {
            val error = e.javaClass.simpleName
            log.warn("POS places sync failed: connectionId={}, error={}", connection.id, error, e)
            steps += SyncStepRow(PLACES, SyncJobRepository.FAILED, error)
        }
        syncJobs.finish(job, steps, clock.instant())
        val failed = steps.firstOrNull { it.status != SyncJobRepository.SUCCEEDED }
        if (failed == null) {
            connections.markSynced(connection.id, clock.instant())
        } else {
            connections.markFailed(connection.id, failed.error ?: failed.name, clock.instant())
        }
    }

    private companion object {
        const val INCREMENTAL = "INCREMENTAL"
        const val PLACES = "places"
    }
}
