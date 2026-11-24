package com.onemind.sales.data

import com.onemind.core.data.cache.ScreenCache
import com.onemind.core.data.cache.screenCacheKey
import com.onemind.core.data.network.ApiJson
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.sales.domain.CachedSales
import com.onemind.sales.domain.SalesPeriod
import com.onemind.sales.domain.SalesRepository
import com.onemind.sales.domain.SalesSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.onemind.core.api.model.SalesSummary as SalesSummaryDto

/**
 * `GET /dashboard/sales` with ETag polling (ADR 0005) and the read cache (ADR 0006):
 * - the last answer and its `ETag` are kept in memory for the latest venue set and period and sent as
 *   `If-None-Match`; a `304` returns that answer again;
 * - every fresh answer is also written to the [ScreenCache] as the raw body the server sent (not
 *   re-encoded: values this app version reads as `UNKNOWN` stay as sent, for a newer version to read),
 *   so the next launch can fall back to it ([cachedSales]) and its first poll sends the cached `ETag`;
 * - a `304` with nothing usable to return (no readable entry) is fetched again without `If-None-Match`,
 *   never turned into an empty screen.
 */
class SalesRepositoryImpl(
    private val client: HttpClient,
    private val cache: ScreenCache,
) : SalesRepository {
    /** One request: the sorted venue set and the period. */
    private data class Query(
        val venueIds: String,
        val period: SalesPeriod,
    )

    private class Last(
        val query: Query,
        val etag: String,
        val summary: SalesSummary,
    )

    private val mutex = Mutex()
    private var last: Last? = null

    override suspend fun getSales(
        venueIds: List<String>,
        period: SalesPeriod,
    ): Result<SalesSummary, DataError> {
        // The server answers 400 VALIDATION_FAILED without a venue; say so without the round trip.
        if (venueIds.isEmpty()) return Result.Failure(DataError.Api(HttpStatusCode.BadRequest.value, VALIDATION_FAILED))
        // Sorted, so the same set is the same request, the same ETag key and the same cache key in any order.
        val query = Query(venueIds.distinct().sorted().joinToString(","), period)
        val known = mutex.withLock { last?.takeIf { it.query == query } } ?: fromCache(query)
        // Taken before the request: if a sign-out clears the cache meanwhile, this answer isn't written back.
        val generation = cache.generation
        val result = fetch(query, known?.etag)
        if (result is Fetched.NotModified) {
            if (known != null) {
                mutex.withLock { last = known }
                return Result.Success(known.summary)
            }
            // Can't happen without If-None-Match, but never end empty: ask for the full answer.
            return fetch(query, etag = null).toResult(query, generation)
        }
        return result.toResult(query, generation)
    }

    override suspend fun cachedSales(
        venueIds: List<String>,
        period: SalesPeriod,
    ): CachedSales? {
        if (venueIds.isEmpty()) return null
        val entry = cache.read(cacheKey(Query(venueIds.distinct().sorted().joinToString(","), period))) ?: return null
        return decode(entry.body)?.let { CachedSales(it, entry.savedAt) }
    }

    private sealed interface Fetched {
        data class Body(
            val raw: String,
            val etag: String?,
        ) : Fetched

        data object NotModified : Fetched

        data class Failed(
            val error: DataError,
        ) : Fetched
    }

    private suspend fun fetch(
        query: Query,
        etag: String?,
    ): Fetched {
        var responseEtag: String? = null
        val result =
            safeCall<String> {
                client
                    .get("dashboard/sales") {
                        parameter("venueIds", query.venueIds)
                        parameter("period", query.period.name)
                        etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    }.also { responseEtag = it.headers[HttpHeaders.ETag] }
            }
        return when (result) {
            is Result.Success -> {
                Fetched.Body(result.data, responseEtag)
            }

            is Result.Failure -> {
                val error = result.error
                // safeCall reads every non-2xx as a problem; a 304 is "unchanged since that ETag".
                if (error is DataError.Api &&
                    error.status == HttpStatusCode.NotModified.value
                ) {
                    Fetched.NotModified
                } else {
                    Fetched.Failed(error)
                }
            }
        }
    }

    private suspend fun Fetched.toResult(
        query: Query,
        generation: Long,
    ): Result<SalesSummary, DataError> =
        when (this) {
            is Fetched.Body -> {
                val mapped = parse(raw)
                if (mapped is Result.Success) {
                    mutex.withLock { last = etag?.let { Last(query, it, mapped.data) } }
                    cache.write(cacheKey(query), raw, etag, generation)
                }
                mapped
            }

            // A second 304 to a request without If-None-Match: the server broke the contract.
            Fetched.NotModified -> {
                Result.Failure(DataError.Network.UNKNOWN)
            }

            is Fetched.Failed -> {
                Result.Failure(error)
            }
        }

    /** The cached answer as a known version to poll against, if its entry is still readable. */
    private suspend fun fromCache(query: Query): Last? {
        val entry = cache.read(cacheKey(query)) ?: return null
        val etag = entry.etag ?: return null
        return decode(entry.body)?.let { Last(query, etag, it) }
    }

    /** The body as a summary: unreadable JSON is `SERIALIZATION`; the mapper decides the rest. */
    private fun parse(body: String): Result<SalesSummary, DataError> =
        try {
            ApiJson.decodeFromString(SalesSummaryDto.serializer(), body).toDomainResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(DataError.Network.SERIALIZATION)
        }

    /** A cached entry this app version can't read is no cache at all. */
    private fun decode(body: String): SalesSummary? = (parse(body) as? Result.Success)?.data

    private fun cacheKey(query: Query) =
        screenCacheKey("dashboard/sales", mapOf("venueIds" to query.venueIds, "period" to query.period.name))

    private companion object {
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
    }
}
