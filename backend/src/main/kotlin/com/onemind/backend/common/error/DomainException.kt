package com.onemind.backend.common.error

import java.time.Duration

/**
 * Rule violations and failures that end a request with an RFC 9457 problem (docs/api/README.md#errors).
 * Expected outcomes the caller must handle are sealed return types instead, not exceptions.
 *
 * The hierarchy is sealed so [ApiExceptionHandler][com.onemind.backend.common.web.ApiExceptionHandler] maps every category to its HTTP status in one
 * exhaustive `when`. Modules extend the abstract categories below with their own exceptions.
 *
 * @param code stable machine-readable code (`SCREAMING_SNAKE`); clients switch on it.
 * @param detail English fallback text for clients that don't know [code] yet. No personal data.
 * @param params values the app needs to build its own message for [code].
 */
sealed class DomainException(
    val code: String,
    detail: String,
    val params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(detail, cause) {
    init {
        require(CODE_PATTERN.matches(code)) { "Error code must be SCREAMING_SNAKE: $code" }
    }

    val detail: String get() = message.orEmpty()

    private companion object {
        val CODE_PATTERN = Regex("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$")
    }
}

/** `400`: the request is malformed or misses something required. */
abstract class InvalidRequestException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `401`: not signed in, or the credentials (token, refresh token) are not valid. */
abstract class UnauthorizedException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `403`: the user may not do this in the venue. Resources of other venues are [NotFoundException]. */
abstract class ForbiddenException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `404`: not found, also for resources in venues the user can't access, so they aren't revealed. */
abstract class NotFoundException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `409`: conflicts with the current state of the resource (version, state). */
abstract class ConflictException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `422`: the request is well-formed but breaks a business rule. */
abstract class RuleViolationException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : DomainException(code, detail, params, cause)

/** `429`: too many requests; the client may retry after [retryAfter] (`Retry-After`, `params.retryAfterSeconds`). */
abstract class TooManyRequestsException(
    code: String,
    detail: String,
    val retryAfter: Duration,
    params: Map<String, Any> = emptyMap(),
) : DomainException(code, detail, params + ("retryAfterSeconds" to retryAfter.toSeconds().coerceAtLeast(1)))

/** `426`: the app is older than the minimum supported version and must be updated (ADR 0001). */
abstract class UpgradeRequiredException(
    code: String,
    detail: String,
    params: Map<String, Any> = emptyMap(),
) : DomainException(code, detail, params)

/** A rate limit without a more specific code. */
class RateLimitedException(
    retryAfter: Duration,
) : TooManyRequestsException("RATE_LIMITED", "Too many requests. Try again later.", retryAfter)

/** The `version` sent with a PUT or command doesn't match the stored one (docs/api/README.md). */
class VersionConflictException(
    expectedVersion: Int,
    actualVersion: Int,
) : ConflictException(
        code = "VERSION_CONFLICT",
        detail = "This item was changed by someone else. Reload it and try again.",
        params = mapOf("expectedVersion" to expectedVersion, "actualVersion" to actualVersion),
    )
