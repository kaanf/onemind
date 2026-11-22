package com.onemind.today.data

import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.today.domain.TodayRepository
import com.onemind.today.domain.TodaySummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.onemind.core.api.model.TodaySummary as TodaySummaryDto

/**
 * `GET /dashboard/today` with ETag polling (ADR 0005): the last answer and its `ETag` are kept in
 * memory for the latest venue set and sent as `If-None-Match`; a `304` returns that answer again.
 * Switching venues starts fresh. The offline copy is the read cache's job (ADR 0006), not this one.
 */
class TodayRepositoryImpl(
    private val client: HttpClient,
) : TodayRepository {
    private class Last(
        val venueSet: String,
        val etag: String,
        val summary: TodaySummary,
    )

    private val mutex = Mutex()
    private var last: Last? = null

    override suspend fun getToday(venueIds: List<String>): Result<TodaySummary, DataError> {
        // The server answers 400 VALIDATION_FAILED without a venue; say so without the round trip.
        if (venueIds.isEmpty()) return Result.Failure(DataError.Api(HttpStatusCode.BadRequest.value, VALIDATION_FAILED))
        // Sorted, so the same set is the same request and the same ETag key in any order.
        val venueSet = venueIds.distinct().sorted().joinToString(",")
        val previous = mutex.withLock { last?.takeIf { it.venueSet == venueSet } }
        var etag: String? = null
        val result =
            safeCall<TodaySummaryDto> {
                client
                    .get("dashboard/today") {
                        parameter("venueIds", venueSet)
                        previous?.let { header(HttpHeaders.IfNoneMatch, it.etag) }
                    }.also { etag = it.headers[HttpHeaders.ETag] }
            }
        return when (result) {
            is Result.Failure -> {
                val error = result.error
                // safeCall reads every non-2xx as a problem; a 304 is "unchanged since `previous`".
                if (previous != null && error is DataError.Api && error.status == HttpStatusCode.NotModified.value) {
                    Result.Success(previous.summary)
                } else {
                    result
                }
            }

            is Result.Success -> {
                val mapped = result.data.toDomainResult()
                if (mapped is Result.Success) mutex.withLock { last = etag?.let { Last(venueSet, it, mapped.data) } }
                mapped
            }
        }
    }

    private companion object {
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
    }
}
