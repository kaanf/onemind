package com.onemind.sales.data.losses

import com.onemind.core.data.network.ApiJson
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.data.http.EtagGet
import com.onemind.sales.data.http.NoVenueError
import com.onemind.sales.data.http.venueSet
import com.onemind.sales.domain.losses.LossesPeriod
import com.onemind.sales.domain.losses.LossesRepository
import com.onemind.sales.domain.losses.LossesSummary
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import com.onemind.core.api.model.LossesSummary as LossesSummaryDto

/** `GET /dashboard/losses` with ETag revalidation (ADR 0005). No read cache yet (ADR 0006 covers Today first). */
class LossesRepositoryImpl(
    client: HttpClient,
) : LossesRepository {
    private val get = EtagGet(client, "dashboard/losses", ::parse)

    override suspend fun getLosses(
        venueIds: List<String>,
        period: LossesPeriod,
    ): Result<LossesSummary, DataError> {
        if (venueIds.isEmpty()) return Result.Failure(NoVenueError)
        return get.get(listOf("venueIds" to venueSet(venueIds), "period" to period.name))
    }

    /** The body as a summary: unreadable JSON is `SERIALIZATION`; the mapper decides the rest. */
    private fun parse(body: String): Result<LossesSummary, DataError> =
        try {
            ApiJson.decodeFromString(LossesSummaryDto.serializer(), body).toDomainResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(DataError.Network.SERIALIZATION)
        }
}
