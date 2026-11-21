package com.onemind.backend.common.error

import io.micrometer.tracing.Tracer
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Builds the RFC 9457 problems of the API (docs/api/README.md#errors, `Problem` in the spec):
 * `type` and `title` derived from `code`, plus the `traceId` of the current request. Used by
 * [ApiExceptionHandler] and by filters that answer before Spring MVC.
 */
@Component
class Problems(
    private val tracer: Tracer,
) {
    fun of(
        status: HttpStatusCode,
        code: String,
        detail: String?,
        params: Map<String, Any> = emptyMap(),
    ): ProblemDetail =
        enrich(
            ProblemDetail.forStatusAndDetail(status, detail).apply { title = titleOf(code) },
            code,
        ).apply { if (params.isNotEmpty()) setProperty(PARAMS, params) }

    /** Adds `type`, `code` and `traceId` to a problem Spring created; keeps a `code` already set. */
    fun enrich(
        problem: ProblemDetail,
        defaultCode: String = codeOf(HttpStatusCode.valueOf(problem.status)),
    ): ProblemDetail {
        val code = problem.properties?.get(CODE) as? String ?: defaultCode
        problem.type = URI.create(TYPE_BASE + code.lowercase().replace('_', '-'))
        problem.setProperty(CODE, code)
        currentTraceId()?.let { problem.setProperty(TRACE_ID, it) }
        return problem
    }

    private fun currentTraceId(): String? =
        tracer
            .currentSpan()
            ?.context()
            ?.traceId()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val TYPE_BASE = "https://api.onemind.app/problems/"
        const val CODE = "code"
        const val PARAMS = "params"
        const val TRACE_ID = "traceId"
        const val ERRORS = "errors"

        /** Code for problems without a domain code: the status name, e.g. `NOT_FOUND`. */
        fun codeOf(status: HttpStatusCode): String = HttpStatus.resolve(status.value())?.name ?: "HTTP_${status.value()}"

        /** `RECEIPT_ALREADY_APPROVED` → `Receipt already approved`. */
        fun titleOf(code: String): String = code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
