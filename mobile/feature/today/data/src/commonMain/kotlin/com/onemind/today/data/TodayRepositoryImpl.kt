package com.onemind.today.data

import com.onemind.core.data.cache.ScreenCache
import com.onemind.core.data.cache.screenCacheKey
import com.onemind.core.data.network.ApiJson
import com.onemind.core.data.network.safeCall
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.today.domain.CachedToday
import com.onemind.today.domain.TodayRepository
import com.onemind.today.domain.TodaySummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.onemind.core.api.model.TodaySummary as TodaySummaryDto

/**
 * `GET /dashboard/today` with ETag polling (ADR 0005) and the read cache (ADR 0006):
 * - the last answer and its `ETag` are kept in memory for the latest venue set and sent as
 *   `If-None-Match`; a `304` returns that answer again;
 * - every fresh answer is also written to the [ScreenCache] as the raw body the server sent (not
 *   re-encoded: values this app version reads as `UNKNOWN` stay as sent, for a newer version to read),
 *   so the next launch can fall back to it ([cachedToday]) and its first poll sends the cached `ETag`;
 * - a `304` with nothing usable to return (no readable entry) is fetched again without `If-None-Match`,
 *   never turned into an empty screen.
 */
class TodayRepositoryImpl(
    private val client: HttpClient,
    private val cache: ScreenCache,
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
        // Sorted, so the same set is the same request, the same ETag key and the same cache key in any order.
        val venueSet = venueIds.distinct().sorted().joinToString(",")
        val known = mutex.withLock { last?.takeIf { it.venueSet == venueSet } } ?: fromCache(venueSet)
        // Taken before the request: if a sign-out clears the cache meanwhile, this answer isn't written back.
        val generation = cache.generation
        val result = fetch(venueSet, known?.etag)
        if (result is Fetched.NotModified) {
            if (known != null) {
                mutex.withLock { last = known }
                return Result.Success(known.summary)
            }
            // Can't happen without If-None-Match, but never end empty: ask for the full answer.
            return fetch(venueSet, etag = null).toResult(venueSet, generation)
        }
        return result.toResult(venueSet, generation)
    }

    override suspend fun cachedToday(venueIds: List<String>): CachedToday? {
        if (venueIds.isEmpty()) return null
        val entry = cache.read(cacheKey(venueIds.distinct().sorted().joinToString(","))) ?: return null
        return decode(entry.body)?.let { CachedToday(it, entry.savedAt) }
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
        venueSet: String,
        etag: String?,
    ): Fetched {
        var responseEtag: String? = null
        val result =
            safeCall<String> {
                client
                    .get("dashboard/today") {
                        parameter("venueIds", venueSet)
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
        venueSet: String,
        generation: Long,
    ): Result<TodaySummary, DataError> =
        when (this) {
            is Fetched.Body -> {
                val mapped = parse(raw)
                if (mapped is Result.Success) {
                    mutex.withLock { last = etag?.let { Last(venueSet, it, mapped.data) } }
                    cache.write(cacheKey(venueSet), raw, etag, generation)
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
    private suspend fun fromCache(venueSet: String): Last? {
        val entry = cache.read(cacheKey(venueSet)) ?: return null
        val etag = entry.etag ?: return null
        return decode(entry.body)?.let { Last(venueSet, etag, it) }
    }

    /** The body as a summary: unreadable JSON is `SERIALIZATION`; the mapper decides the rest. */
    private fun parse(body: String): Result<TodaySummary, DataError> =
        try {
            ApiJson.decodeFromString(TodaySummaryDto.serializer(), body).toDomainResult()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.Failure(DataError.Network.SERIALIZATION)
        }

    /** A cached entry this app version can't read is no cache at all. */
    private fun decode(body: String): TodaySummary? = (parse(body) as? Result.Success)?.data

    private fun cacheKey(venueSet: String) = screenCacheKey("dashboard/today", mapOf("venueIds" to venueSet))

    private companion object {
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
    }
}
