package com.onemind.core.data.config

import com.onemind.core.data.BuildKonfig

/** The build variants of ADR 0012 §2; [key] is the `onemind.environment` value. */
enum class AppEnvironment(
    val key: String,
) {
    DEV("dev"),
    PROD("prod"),
    ;

    companion object {
        fun fromKey(key: String): AppEnvironment = entries.firstOrNull { it.key == key } ?: error("Unknown environment '$key'")
    }
}

/**
 * Where the app talks to: the API host of the build's environment.
 * [baseUrl] is the host without `/api/v1`; the HTTP client appends the spec's server path.
 */
data class ApiConfig(
    val environment: AppEnvironment,
    val baseUrl: String,
) {
    companion object {
        /** Values compiled in by BuildKonfig for the selected `onemind.environment`. */
        fun fromBuild(): ApiConfig =
            ApiConfig(environment = AppEnvironment.fromKey(BuildKonfig.ENVIRONMENT), baseUrl = BuildKonfig.API_BASE_URL)
    }
}
