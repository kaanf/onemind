package com.onemind.backend.common.idempotency

import com.onemind.backend.common.idempotency.infra.persistence.IdempotencyClaim
import com.onemind.backend.common.idempotency.infra.persistence.IdempotencyRecordRepository
import com.onemind.backend.common.security.AuthenticationRequiredException
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.util.ContentCachingResponseWrapper
import org.springframework.web.util.ServletRequestPathUtils
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * `Idempotency-Key` semantics of the IETF draft for POSTs (docs/api/README.md, ADR 0010): the first
 * request with a key runs and its response is stored for 24 h; a retry with the same request gets
 * that response again, a different request with the key gets `422`, a retry while the first is
 * still running gets `409`. Responses with `5xx` (or an exception) are not stored, so the client can
 * retry with the same key. Only operations that declare the `Idempotency-Key` header parameter in the
 * API spec (and so in their generated handler method) are tracked; a header sent to any other
 * endpoint is ignored, so responses with secrets (sign-in tokens) are never stored. A missing
 * required key is a `400` from Spring MVC.
 *
 * Keys are scoped per user: the UUID in the authentication name (the access token's `sub`, ADR 0004);
 * requests without such a user pass through untouched.
 */
@Component
// After Spring Security's filter chain (order -100), which provides the authenticated user.
@Order(0)
class IdempotencyFilter(
    private val records: IdempotencyRecordRepository,
    private val clock: Clock,
    @param:Qualifier("handlerExceptionResolver") private val exceptionResolver: HandlerExceptionResolver,
    @param:Qualifier("requestMappingHandlerMapping") private val handlerMapping: RequestMappingHandlerMapping,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method != "POST" || request.getHeader(HEADER) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!declaresKey(request)) return filterChain.doFilter(request, response)
        val userId = currentUserId() ?: return filterChain.doFilter(request, response)
        val key =
            parseKey(request.getHeader(HEADER))
                ?: return fail(request, response, InvalidIdempotencyKeyException())
        val (hashedRequest, hash) = hash(request)
        val claim =
            records.claim(userId, key, request.method, request.requestURI, hash, clock.instant(), TTL, LOCK_TIMEOUT)
        when (claim) {
            is IdempotencyClaim.Acquired -> proceed(hashedRequest, response, filterChain, userId, key, claim.claimId)
            IdempotencyClaim.InProgress -> fail(request, response, IdempotentRequestInProgressException())
            IdempotencyClaim.Mismatch -> fail(request, response, IdempotencyKeyReusedException())
            is IdempotencyClaim.Replay -> replay(response, claim)
            IdempotencyClaim.UnknownUser -> fail(request, response, AuthenticationRequiredException())
        }
    }

    private fun proceed(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
        userId: UUID,
        key: UUID,
        claimId: UUID,
    ) {
        val cached = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, cached)
        } catch (e: Throwable) {
            records.release(userId, key, claimId)
            throw e
        }
        try {
            val body = cached.contentAsByteArray
            if (cached.status >= 500 || !isStorable(body, cached.contentType)) {
                records.release(userId, key, claimId)
            } else {
                val headers =
                    REPLAYED_HEADERS.mapNotNull { name -> cached.getHeader(name)?.let { name to it } }.toMap()
                records.complete(userId, key, claimId, cached.status, headers, body.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // The request itself succeeded; its response is sent. A retry after the lock timeout runs it again.
            log.error("Could not store the response of an idempotent request", e)
        } finally {
            cached.copyBodyToResponse()
        }
    }

    private fun replay(
        response: HttpServletResponse,
        replay: IdempotencyClaim.Replay,
    ) {
        response.status = replay.status
        replay.headers.forEach(response::setHeader)
        replay.body?.let { response.outputStream.write(it.toByteArray(Charsets.UTF_8)) }
    }

    // Answered by ApiExceptionHandler, so the problem looks like any other.
    private fun fail(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: Exception,
    ) {
        exceptionResolver.resolveException(request, response, null, exception)
    }

    /** SHA-256 of method, path, query and body; returns a request whose body can be read again. */
    private fun hash(request: HttpServletRequest): Pair<HttpServletRequest, ByteArray> {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("${request.method} ${request.requestURI}?${request.queryString.orEmpty()}\n".toByteArray())
        // Multipart boundaries differ between retries, so hash the parts instead of the raw body.
        if (request.contentType?.startsWith("multipart/", ignoreCase = true) == true) {
            request.parts.sortedBy { it.name }.forEach { part ->
                digest.update("${part.name}\u0000${part.submittedFileName}\u0000${part.contentType}\u0000${part.size}\n".toByteArray())
                part.inputStream.use { digest.update(it.readAllBytes()) }
            }
            return request to digest.digest()
        }
        val body = request.inputStream.readAllBytes()
        digest.update(body)
        return BufferedBodyRequest(request, body) to digest.digest()
    }

    /** Whether the handler of this request takes the `Idempotency-Key` header (declared in the spec). */
    private fun declaresKey(request: HttpServletRequest): Boolean {
        // Handler lookup before the DispatcherServlet needs the parsed path, as the servlet would cache it.
        val previousPath = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE)
        ServletRequestPathUtils.parseAndCache(request)
        try {
            val handler = runCatching { handlerMapping.getHandler(request)?.handler }.getOrNull() as? HandlerMethod ?: return false
            return handler.methodParameters.any { parameter ->
                parameter
                    .getParameterAnnotation(
                        RequestHeader::class.java,
                    )?.let { HEADER.equals(it.name.ifEmpty { it.value }, ignoreCase = true) } ==
                    true
            }
        } finally {
            request.setAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE, previousPath)
        }
    }

    private fun currentUserId(): UUID? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return parseKey(authentication.name)
    }

    private class BufferedBodyRequest(
        request: HttpServletRequest,
        private val body: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val input = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = input.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = input.read(b, off, len)

                override fun isFinished(): Boolean = input.available() == 0

                override fun isReady(): Boolean = true

                override fun setReadListener(listener: ReadListener) = throw UnsupportedOperationException()
            }
        }

        override fun getReader(): BufferedReader = inputStream.bufferedReader(characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8)
    }

    companion object {
        const val HEADER = "Idempotency-Key"
        val TTL: Duration = Duration.ofHours(24)

        /** A record left `IN_PROGRESS` this long belongs to a crashed request and may be taken over. */
        val LOCK_TIMEOUT: Duration = Duration.ofMinutes(5)

        private val REPLAYED_HEADERS = listOf(HttpHeaders.CONTENT_TYPE, HttpHeaders.LOCATION)

        /** Canonical UUIDs only (`UUID.fromString` also accepts forms like `1-1-1-1-1`). */
        private fun parseKey(value: String): UUID? =
            runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString() == value.lowercase() }

        private fun isStorable(
            body: ByteArray,
            contentType: String?,
        ): Boolean {
            if (body.isEmpty()) return true
            val type = contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() } ?: return false
            return type.subtype == "json" || type.subtype.endsWith("+json")
        }
    }
}
