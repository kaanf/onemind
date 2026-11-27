package com.onemind.staff.data

import com.onemind.core.data.network.ApiJson
import com.onemind.core.data.network.EtagGet
import com.onemind.core.data.network.NoVenueError
import com.onemind.core.data.network.venueSet
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.staff.domain.StaffRepository
import com.onemind.staff.domain.StaffSummary
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import com.onemind.core.api.model.StaffSummary as StaffSummaryDto

/** `GET /dashboard/staff` with ETag revalidation (ADR 0005). No read cache yet (ADR 0006 covers Today first). */
class StaffRepositoryImpl(
    client: HttpClient,
) : StaffRepository {
    private val get = EtagGet(client, "dashboard/staff", ::parse)

    override suspend fun getStaff(venueIds: List<String>): Result<StaffSummary, DataError> {
        if (venueIds.isEmpty()) return Result.Failure(NoVenueError)
        return get.get(listOf("venueIds" to venueSet(venueIds)))
    }

    /** The body as a summary: unreadable JSON is `SERIALIZATION`; the mapper decides the rest. */
    private fun parse(body: String): Result<StaffSummary, DataError> =
        try {
            ApiJson.decodeFromString(StaffSummaryDto.serializer(), body).toDomainResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(DataError.Network.SERIALIZATION)
        }
}
