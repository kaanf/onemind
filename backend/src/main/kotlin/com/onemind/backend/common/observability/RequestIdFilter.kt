package com.onemind.backend.common.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Puts the client's `X-Request-Id` into the MDC as `requestId`, so every log line of the request
 * carries it next to `traceId` (ADR 0012). Values that are not a UUID are ignored: the header is
 * client input and must not be able to forge log content.
 */
@Component
// After the server observation filter (HIGHEST_PRECEDENCE + 1), so the trace already exists, and
// before Spring Security, so rejected requests are logged with the id too.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { UUID_PATTERN.matches(it) }
        if (requestId == null) {
            filterChain.doFilter(request, response)
            return
        }
        MDC.putCloseable(MDC_KEY, requestId).use { filterChain.doFilter(request, response) }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private val UUID_PATTERN =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
