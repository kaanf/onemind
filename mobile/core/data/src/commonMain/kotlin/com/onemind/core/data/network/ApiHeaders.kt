package com.onemind.core.data.network

/** Header names the API reads (`docs/api/README.md`). */
internal object ApiHeaders {
    const val APP_VERSION = "X-App-Version"
    const val PLATFORM = "X-Platform"
    const val REQUEST_ID = "X-Request-Id"
}

/** `X-Platform` value: `IOS` or `ANDROID` (`docs/api/components/parameters/Platform.yaml`). */
internal expect val platformHeaderValue: String
