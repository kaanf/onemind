package com.onemind.backend.common.security

import com.onemind.backend.common.error.ForbiddenException
import com.onemind.backend.common.error.UnauthorizedException
import com.onemind.backend.common.web.ApiPathConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.servlet.HandlerExceptionResolver

class AuthenticationRequiredException : UnauthorizedException("UNAUTHORIZED", "Sign in to continue.")

class AccessDeniedProblemException : ForbiddenException("FORBIDDEN", "You don't have access to this.")

/**
 * Stateless JWT resource server (ADR 0004). Only health and the sign-in endpoints are public;
 * everything else needs a valid access token. 401/403 answers are RFC 9457 problems like every other
 * error (through [ApiExceptionHandler][com.onemind.backend.common.error.ApiExceptionHandler]), with
 * the `WWW-Authenticate` header of RFC 6750.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("handlerExceptionResolver") exceptionResolver: HandlerExceptionResolver,
    ): SecurityFilterChain {
        val entryPoint = problemEntryPoint(exceptionResolver)
        val accessDenied = problemAccessDeniedHandler(exceptionResolver)
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                it.requestMatchers(HttpMethod.POST, *PUBLIC_POSTS.map { path -> ApiPathConfig.BASE_PATH + path }.toTypedArray()).permitAll()
                it.anyRequest().authenticated()
            }.oauth2ResourceServer {
                it.jwt { }
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(accessDenied)
            }.exceptionHandling {
                it.authenticationEntryPoint(entryPoint)
                it.accessDeniedHandler(accessDenied)
            }.build()
    }

    private fun problemEntryPoint(resolver: HandlerExceptionResolver): AuthenticationEntryPoint {
        val bearer = BearerTokenAuthenticationEntryPoint()
        return AuthenticationEntryPoint { request, response, exception ->
            bearer.commence(request, response, exception)
            resolver.resolveException(request, response, null, AuthenticationRequiredException())
        }
    }

    private fun problemAccessDeniedHandler(resolver: HandlerExceptionResolver): AccessDeniedHandler {
        val bearer = BearerTokenAccessDeniedHandler()
        return AccessDeniedHandler { request, response, exception ->
            bearer.handle(request, response, exception)
            resolver.resolveException(request, response, null, AccessDeniedProblemException())
        }
    }

    private companion object {
        val PUBLIC_POSTS = listOf("/auth/email/start", "/auth/email/verify", "/auth/apple", "/auth/google", "/auth/refresh", "/auth/logout")
    }
}
