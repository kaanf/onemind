package com.onemind.backend.common.web

import com.onemind.backend.common.error.InvalidRequestException
import com.onemind.backend.common.error.UpgradeRequiredException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerExceptionResolver

/**
 * Lowest supported app version per platform (`X-Platform`). Blank: no check for that platform.
 */
@ConfigurationProperties("onemind.app-version")
data class AppVersionProperties(
    val minimumIos: String = "",
    val minimumAndroid: String = "",
)

class AppUpgradeRequiredException(
    platform: String,
    minimum: AppVersion,
) : UpgradeRequiredException(
        code = "APP_UPGRADE_REQUIRED",
        detail = "This version of the app is no longer supported. Update it to continue.",
        params = mapOf("platform" to platform, "minimumVersion" to minimum.toString()),
    )

class AppVersionInvalidException :
    InvalidRequestException(
        code = "APP_VERSION_INVALID",
        detail = "The X-App-Version header is not a version number.",
    )

/**
 * Answers `426 APP_UPGRADE_REQUIRED` to apps older than their platform's minimum (ADR 0001,
 * docs/api/README.md). Only `/api/v1` is checked, before authentication, so an outdated app is told
 * to update before anything else (also on sign-in and refresh); health checks are never affected.
 * A request without `X-Platform` or `X-App-Version`, or for a platform without a minimum, passes:
 * the app always sends both, other callers (tools, the web) aren't apps. A version that doesn't
 * parse is `400 APP_VERSION_INVALID` when that platform has a minimum.
 */
@Component
@EnableConfigurationProperties(AppVersionProperties::class)
// After tracing and the request ID (problems carry the traceId), before Spring Security (-100).
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class MinimumAppVersionFilter(
    properties: AppVersionProperties,
    @Qualifier("handlerExceptionResolver") private val exceptionResolver: HandlerExceptionResolver,
) : OncePerRequestFilter() {
    private val minimums: Map<String, AppVersion> =
        mapOf(IOS to properties.minimumIos, ANDROID to properties.minimumAndroid)
            .filterValues { it.isNotBlank() }
            .mapValues { (platform, value) ->
                requireNotNull(AppVersion.parse(value)) { "onemind.app-version minimum for $platform is not a version: $value" }
            }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        minimums.isEmpty() || !request.requestURI.startsWith(ApiPathConfig.BASE_PATH + "/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val platform = request.getHeader(PLATFORM_HEADER)?.trim()?.uppercase()
        val minimum = platform?.let(minimums::get)
        val sent = request.getHeader(VERSION_HEADER)
        if (minimum == null || sent == null) return filterChain.doFilter(request, response)
        val version = AppVersion.parse(sent)
        val rejection =
            when {
                version == null -> AppVersionInvalidException()
                version < minimum -> AppUpgradeRequiredException(platform, minimum)
                else -> null
            }
        if (rejection == null) return filterChain.doFilter(request, response)
        exceptionResolver.resolveException(request, response, null, rejection)
    }

    companion object {
        const val VERSION_HEADER = "X-App-Version"
        const val PLATFORM_HEADER = "X-Platform"
        const val IOS = "IOS"
        const val ANDROID = "ANDROID"
    }
}
