package com.onemind.core.domain.util

/** Why a backend call failed. Presentation maps these to localised strings. */
sealed interface DataError : Error {
    /** No usable response: offline, timeout, or a body the app can't read. */
    enum class Network : DataError {
        NO_INTERNET,
        TIMEOUT,
        SERIALIZATION,

        /** The answer carries a value this app version can't interpret safely (added to the API later): updating the app fixes it. */
        NEEDS_APP_UPDATE,
        UNKNOWN,
    }

    /**
     * An RFC 9457 problem from the API (`docs/api/README.md`). [code] is the stable machine code
     * (`EMAIL_CODE_INVALID`), [detail] the server's English fallback for unknown codes, and [params]
     * the primitive `params` values as strings (`attemptsLeft` → `"3"`).
     */
    data class Api(
        val status: Int,
        val code: String,
        val detail: String? = null,
        val params: Map<String, String> = emptyMap(),
    ) : DataError
}
