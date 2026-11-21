package com.onemind.backend.common.web

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Serves controllers that implement a generated API interface under the spec's server URL
 * (`servers: /api/v1` in `docs/api/openapi.yaml`); the generated paths don't include it.
 */
@Configuration(proxyBeanMethods = false)
class ApiPathConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(BASE_PATH) { type -> type.interfaces.any { it.packageName == API_PACKAGE } }
    }

    companion object {
        const val BASE_PATH = "/api/v1"
        private const val API_PACKAGE = "com.onemind.api.v1.web"
    }
}
