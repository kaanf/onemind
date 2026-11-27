package com.onemind.core.data.network

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A `GET` with ETag revalidation (ADR 0005) for the dashboard screens: the last parsed answer and its
 * `ETag` are kept in memory for the latest query and sent as `If-None-Match`; a `304` returns that
 * answer again. A `304` with nothing to return is fetched again without `If-None-Match`, never turned
 * into an empty screen. Only answers that [parse] accepts are remembered.
 */
class EtagGet<T>(
    private val client: HttpClient,
    private val path: String,
    private val parse: (String) -> Result<T, DataError>,
) {
    private class Last<T>(
        val query: List<Pair<String, String>>,
        val etag: String,
        val value: T,
    )

    private val mutex = Mutex()
    private var last: Last<T>? = null

    suspend fun get(query: List<Pair<String, String>>): Result<T, DataError> {
        val known = mutex.withLock { last?.takeIf { it.query == query } }
        val first = fetch(query, known?.etag)
        if (first is Fetched.NotModified) {
            if (known != null) return Result.Success(known.value)
            return fetch(query, etag = null).toResult(query)
        }
        return first.toResult(query)
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
        query: List<Pair<String, String>>,
        etag: String?,
    ): Fetched {
        var responseEtag: String? = null
        val result =
            safeCall<String> {
                client
                    .get(path) {
                        query.forEach { (name, value) -> parameter(name, value) }
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
                if (error is DataError.Api && error.status == HttpStatusCode.NotModified.value) {
                    Fetched.NotModified
                } else {
                    Fetched.Failed(error)
                }
            }
        }
    }

    private suspend fun Fetched.toResult(query: List<Pair<String, String>>): Result<T, DataError> =
        when (this) {
            is Fetched.Body -> {
                val parsed = parse(raw)
                if (parsed is Result.Success) {
                    mutex.withLock { last = etag?.let { Last(query, it, parsed.data) } }
                }
                parsed
            }

            // A second 304 to a request without If-None-Match: the server broke the contract.
            Fetched.NotModified -> {
                Result.Failure(DataError.Network.UNKNOWN)
            }

            is Fetched.Failed -> {
                Result.Failure(error)
            }
        }
}

/** The server answers 400 VALIDATION_FAILED without a venue; say so without the round trip. */
val NoVenueError = DataError.Api(HttpStatusCode.BadRequest.value, "VALIDATION_FAILED")

/** Sorted, so the same venue set is the same request (and the same ETag key) in any order. */
fun venueSet(venueIds: List<String>): String = venueIds.distinct().sorted().joinToString(",")
