package com.onemind.core.domain.util

/** Marker for the error side of [Result]. */
interface Error

/** Outcome of a call that can fail with a typed, expected error (no exceptions across layers). */
sealed interface Result<out D, out E : Error> {
    data class Success<out D>(
        val data: D,
    ) : Result<D, Nothing>

    data class Failure<out E : Error>(
        val error: E,
    ) : Result<Nothing, E>
}

inline fun <D, E : Error, R> Result<D, E>.map(transform: (D) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Failure -> this
    }

fun <D, E : Error> Result<D, E>.asEmpty(): EmptyResult<E> = map { }

inline fun <D, E : Error> Result<D, E>.onSuccess(action: (D) -> Unit): Result<D, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <D, E : Error> Result<D, E>.onFailure(action: (E) -> Unit): Result<D, E> {
    if (this is Result.Failure) action(error)
    return this
}

typealias EmptyResult<E> = Result<Unit, E>
