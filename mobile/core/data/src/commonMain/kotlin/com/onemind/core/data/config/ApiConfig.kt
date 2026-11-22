package com.onemind.core.data.config

import com.onemind.core.data.BuildKonfig

/**
 * Where the app talks to: the API host of the build's environment (`dev`/`prod`, ADR 0012 §2).
 * [baseUrl] is the host without `/api/v1`; the HTTP client appends the spec's server path.
 */
data class ApiConfig(
    val environment: String,
    val baseUrl: String,
) {
    companion object {
        /** Values compiled in by BuildKonfig for the selected `onemind.environment`. */
        fun fromBuild(): ApiConfig = ApiConfig(environment = BuildKonfig.ENVIRONMENT, baseUrl = BuildKonfig.API_BASE_URL)
    }
}
