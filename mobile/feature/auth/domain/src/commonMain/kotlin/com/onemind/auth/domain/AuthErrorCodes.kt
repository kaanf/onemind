package com.onemind.auth.domain

/** Problem codes of the auth endpoints the sign-in screens handle (`docs/api/paths/account`). */
object AuthErrorCodes {
    const val EMAIL_CODE_INVALID = "EMAIL_CODE_INVALID"
    const val EMAIL_CODE_EXPIRED = "EMAIL_CODE_EXPIRED"
    const val EMAIL_CODE_ATTEMPTS_EXCEEDED = "EMAIL_CODE_ATTEMPTS_EXCEEDED"
    const val EMAIL_CODE_RESEND_TOO_SOON = "EMAIL_CODE_RESEND_TOO_SOON"
    const val APP_UPGRADE_REQUIRED = "APP_UPGRADE_REQUIRED"

    /** `params` keys. */
    const val PARAM_ATTEMPTS_LEFT = "attemptsLeft"
    const val PARAM_RETRY_AFTER_SECONDS = "retryAfterSeconds"
}
