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
    private val syncVenue: SyncVenue,
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
        connections.syncable().forEach { connection ->
            try {
                sync(connection, provider)
            } catch (e: RuntimeException) {
                log.error("POS sync of a connection failed: connectionId={}, error={}", connection.id, e.javaClass.simpleName, e)
            }
        }
    }

    /**
     * Places, then every active venue, each venue on its own: a venue that fails is a failed step (and an
     * error on its cursor), not a failed connection. The job is always finished, whatever is thrown.
     */
    private fun sync(
        connection: PosConnectionRow,
        provider: PosProvider,
    ) {
        val job = syncJobs.start(connection.id, INCREMENTAL, clock.instant())
        val steps = mutableListOf<SyncStepRow>()
        var placesError: String? = PLACES_NOT_RUN
        try {
            val places =
                try {
                    syncPlaces.run(connection, provider).also { placesError = null }
                } catch (e: RuntimeException) {
                    placesError = e.javaClass.simpleName
                    log.warn("POS places sync failed: connectionId={}, error={}", connection.id, placesError, e)
                    null
                }
            steps += SyncStepRow(PLACES, if (places != null) SyncJobRepository.SUCCEEDED else SyncJobRepository.FAILED, placesError)
            places?.venues?.filter { it.active }?.forEach { venue ->
                steps +=
                    try {
                        syncVenue.run(
                            connection.id,
                            venue,
                            PosPlaceRef(connection.externalMerchantId, venue.placeId),
                            places.currency,
                            provider,
                        )
                    } catch (e: RuntimeException) {
                        val error = e.javaClass.simpleName
                        log.warn("POS venue sync failed: venueId={}, error={}", venue.venueId, error, e)
                        listOf(SyncStepRow("venue:${venue.venueId}", SyncJobRepository.FAILED, error))
                    }
            }
        } finally {
            syncJobs.finish(job, steps.ifEmpty { listOf(SyncStepRow(PLACES, SyncJobRepository.FAILED, placesError)) }, clock.instant())
            val error = placesError
            if (error ==
                null
            ) {
                connections.markSynced(connection.id, clock.instant())
            } else {
                connections.markFailed(connection.id, error, clock.instant())
            }
        }
    }

    private companion object {
        const val INCREMENTAL = "INCREMENTAL"
        const val PLACES = "places"
        const val PLACES_NOT_RUN = "NotRun"
    }
}
