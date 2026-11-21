package com.onemind.backend.common.web

import com.onemind.api.v1.model.FieldError
import com.onemind.backend.common.error.ConflictException
import com.onemind.backend.common.error.DomainException
import com.onemind.backend.common.error.ForbiddenException
import com.onemind.backend.common.error.InvalidRequestException
import com.onemind.backend.common.error.NotFoundException
import com.onemind.backend.common.error.Problems
import com.onemind.backend.common.error.RuleViolationException
import com.onemind.backend.common.error.TooManyRequestsException
import com.onemind.backend.common.error.UnauthorizedException
import com.onemind.backend.common.error.UpgradeRequiredException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Maps every exception that leaves a controller to an RFC 9457 problem with `code` and `traceId`
 * (ADR 0010): [DomainException]s by category, Spring MVC exceptions by their status, field
 * validation to `400 VALIDATION_FAILED` with `errors[]`, anything else to `500 INTERNAL_ERROR`.
 */
@RestControllerAdvice
class ApiExceptionHandler(
    private val problems: Problems,
) : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(e: DomainException): ResponseEntity<ProblemDetail> {
        val status =
            when (e) {
                is InvalidRequestException -> HttpStatus.BAD_REQUEST
                is UnauthorizedException -> HttpStatus.UNAUTHORIZED
                is ForbiddenException -> HttpStatus.FORBIDDEN
                is NotFoundException -> HttpStatus.NOT_FOUND
                is ConflictException -> HttpStatus.CONFLICT
                is RuleViolationException -> HttpStatus.UNPROCESSABLE_CONTENT
                is TooManyRequestsException -> HttpStatus.TOO_MANY_REQUESTS
                is UpgradeRequiredException -> HttpStatus.UPGRADE_REQUIRED
            }
        // Code only: detail and params may carry business data.
        log.info("Request failed with {} {}", status.value(), e.code)
        val response = ResponseEntity.status(status)
        if (e is TooManyRequestsException) {
            response.header(
                HttpHeaders.RETRY_AFTER,
                e.retryAfter
                    .toSeconds()
                    .coerceAtLeast(1)
                    .toString(),
            )
        }
        return response.body(problems.of(status, e.code, e.detail, e.params))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ProblemDetail> {
        // Let Spring Security's filters answer 401/403 for method-security failures.
        if (e is AccessDeniedException || e is AuthenticationException) throw e
        log.error("Unhandled exception", e)
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity
            .status(status)
            .body(problems.of(status, INTERNAL_ERROR, "Something went wrong on our side."))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.map { FieldError(it.field, constraintCode(it.code)) }
        ex.body.setValidationErrors(errors)
        return super.handleMethodArgumentNotValid(ex, headers, status, request)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors =
            ex.parameterValidationResults.flatMap { result ->
                val field = result.methodParameter.parameterName ?: "arg${result.methodParameter.parameterIndex}"
                result.resolvableErrors.map { FieldError(field, constraintCode(it.codes?.lastOrNull())) }
            }
        ex.body.setValidationErrors(errors)
        return super.handleHandlerMethodValidationException(ex, headers, status, request)
    }

    // Every response of the base class ends here, so its problems get type, code and traceId too.
    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        if (body is ProblemDetail) problems.enrich(body)
        return super.createResponseEntity(body, headers, statusCode, request)
    }

    private fun ProblemDetail.setValidationErrors(errors: List<FieldError>) {
        setProperty(Problems.CODE, VALIDATION_FAILED)
        setProperty(Problems.ERRORS, errors)
    }

    private companion object {
        const val VALIDATION_FAILED = "VALIDATION_FAILED"
        const val INTERNAL_ERROR = "INTERNAL_ERROR"

        /** Bean Validation constraint name to a stable code: `NotBlank` → `NOT_BLANK`. */
        fun constraintCode(constraint: String?): String =
            constraint
                ?.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                ?.uppercase()
                ?: "INVALID"
    }
}
