package com.onemind.core.presentation.util

import com.onemind.core.domain.util.DataError
import com.onemind.core.presentation.generated.resources.Res
import com.onemind.core.presentation.generated.resources.error_no_internet
import com.onemind.core.presentation.generated.resources.error_rate_limited
import com.onemind.core.presentation.generated.resources.error_timeout
import com.onemind.core.presentation.generated.resources.error_unknown

/**
 * The generic message for an error no screen handles itself. Features map their own codes first
 * (`EMAIL_CODE_INVALID` …) and fall back to this: known shared codes get a localised string, an
 * unknown code shows the server's English `detail` (`docs/api/README.md`), else a generic message.
 */
fun DataError.toUiText(): UiText =
    when (this) {
        DataError.Network.NO_INTERNET -> {
            UiText.Resource(Res.string.error_no_internet)
        }

        DataError.Network.TIMEOUT -> {
            UiText.Resource(Res.string.error_timeout)
        }

        DataError.Network.SERIALIZATION, DataError.Network.UNKNOWN -> {
            UiText.Resource(Res.string.error_unknown)
        }

        is DataError.Api -> {
            when (code) {
                "RATE_LIMITED" -> UiText.Resource(Res.string.error_rate_limited)
                else -> detail?.takeIf { it.isNotBlank() }?.let(UiText::Dynamic) ?: UiText.Resource(Res.string.error_unknown)
            }
        }
    }
