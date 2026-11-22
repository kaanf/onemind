package com.onemind.core.data.network

import kotlinx.serialization.json.Json

/** JSON settings for the API: unknown fields are ignored so additive spec changes don't break the app. */
val ApiJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
