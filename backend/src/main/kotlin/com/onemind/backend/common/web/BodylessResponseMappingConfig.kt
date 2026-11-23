package com.onemind.backend.common.web

import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.Method

/**
 * Lets clients that send `Accept: application/json` call endpoints whose success response has no body
 * (`202`/`204`, for example `POST /auth/logout` or `DELETE /me`).
 *
 * For such operations the only content in the spec is the error's `application/problem+json`, so the
 * generated interface declares `produces = ["application/problem+json"]` and Spring would answer `406`
 * at mapping time, before the controller runs. The app's HTTP client sends `Accept: application/json`
 * on every request. A mapping that produces nothing but problems therefore gets no produces condition;
 * its problems are still written as RFC 9457 bodies (`docs/api/README.md`, *Content types*).
 */
@Configuration(proxyBeanMethods = false)
class BodylessResponseMappingConfig {
    @Bean
    fun bodylessResponseMappingRegistrations(): WebMvcRegistrations =
        object : WebMvcRegistrations {
            override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping = BodylessResponseHandlerMapping()
        }

    private class BodylessResponseHandlerMapping : RequestMappingHandlerMapping() {
        override fun getMappingForMethod(
            method: Method,
            handlerType: Class<*>,
        ): RequestMappingInfo? {
            val info = super.getMappingForMethod(method, handlerType) ?: return null
            val produces = info.producesCondition.producibleMediaTypes
            if (produces.isEmpty() || !produces.all { it.equalsTypeAndSubtype(MediaType.APPLICATION_PROBLEM_JSON) }) return info
            return info.mutate().produces().build()
        }
    }
}
