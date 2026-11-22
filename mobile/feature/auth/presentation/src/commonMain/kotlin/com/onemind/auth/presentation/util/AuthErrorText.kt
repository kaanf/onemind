package com.onemind.auth.presentation.util

import com.onemind.auth.domain.AuthErrorCodes
import com.onemind.auth.presentation.generated.resources.Res
import com.onemind.auth.presentation.generated.resources.auth_error_code_attempts_exceeded
import com.onemind.auth.presentation.generated.resources.auth_error_code_expired
import com.onemind.auth.presentation.generated.resources.auth_error_code_invalid
import com.onemind.auth.presentation.generated.resources.auth_error_code_invalid_attempts
import com.onemind.core.domain.util.DataError
import com.onemind.core.presentation.util.UiText
import com.onemind.core.presentation.util.toUiText

/**
 * The message for an auth error, or `null` when the screen shouldn't show one: on
 * `APP_UPGRADE_REQUIRED` the app-wide update screen covers everything.
 */
internal fun DataError.toAuthMessage(): UiText? {
    if (this !is DataError.Api) return toUiText()
    return when (code) {
        AuthErrorCodes.APP_UPGRADE_REQUIRED -> {
            null
        }

        AuthErrorCodes.EMAIL_CODE_INVALID -> {
            params[AuthErrorCodes.PARAM_ATTEMPTS_LEFT]
                ?.let { UiText.Resource(Res.string.auth_error_code_invalid_attempts, listOf(it)) }
                ?: UiText.Resource(Res.string.auth_error_code_invalid)
        }

        AuthErrorCodes.EMAIL_CODE_EXPIRED -> {
            UiText.Resource(Res.string.auth_error_code_expired)
        }

        AuthErrorCodes.EMAIL_CODE_ATTEMPTS_EXCEEDED -> {
            UiText.Resource(Res.string.auth_error_code_attempts_exceeded)
        }

        else -> {
            toUiText()
        }
    }
}

/** `params.retryAfterSeconds` of a `429`, if the server sent one. */
internal fun DataError.retryAfterSeconds(): Int? =
    (this as? DataError.Api)?.params?.get(AuthErrorCodes.PARAM_RETRY_AFTER_SECONDS)?.toIntOrNull()
