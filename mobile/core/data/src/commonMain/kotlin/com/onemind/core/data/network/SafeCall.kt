package com.onemind.core.data.network

import com.onemind.core.api.model.Problem
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Runs one API request and turns every outcome into a [Result]: the body on 2xx, the RFC 9457
 * problem as [DataError.Api] otherwise, and transport failures as [DataError.Network]. Cancellation
 * is rethrown. Nothing here logs, so request bodies (codes, refresh tokens) never reach a log.
 */
suspend inline fun <reified T> safeCall(request: () -> HttpResponse): Result<T, DataError> {
    val response =
        try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: HttpRequestTimeoutException) {
            return Result.Failure(DataError.Network.TIMEOUT)
        } catch (_: IOException) {
            return Result.Failure(DataError.Network.NO_INTERNET)
        } catch (_: Exception) {
            return Result.Failure(DataError.Network.UNKNOWN)
        }
    return response.toResult()
}

suspend inline fun <reified T> HttpResponse.toResult(): Result<T, DataError> {
    if (!status.isSuccess()) return Result.Failure(toApiError())
    if (T::class == Unit::class) return Result.Success(Unit as T)
    return try {
        Result.Success(body<T>())
    } catch (e: CancellationException) {
        throw e
    } catch (_: ContentConvertException) {
        // Ktor wraps kotlinx decoding errors (JsonConvertException).
        Result.Failure(DataError.Network.SERIALIZATION)
    } catch (_: SerializationException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    } catch (_: NoTransformationFoundException) {
        Result.Failure(DataError.Network.SERIALIZATION)
    }
}

/** The response's problem, or a bare status error when the body isn't one (e.g. a proxy page). */
suspend fun HttpResponse.toApiError(): DataError.Api {
    val problem = readProblem()
    return DataError.Api(
        status = status.value,
        code = problem?.code ?: UNKNOWN_CODE,
        detail = problem?.detail,
        params =
            problem?.params.orEmpty().mapValues { (_, value) ->
                (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
            },
    )
}

internal suspend fun HttpResponse.readProblem(): Problem? =
    try {
        ApiJson.decodeFromString<Problem>(bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

/** [DataError.Api.code] when the response carried no problem body. */
const val UNKNOWN_CODE = "UNKNOWN"
