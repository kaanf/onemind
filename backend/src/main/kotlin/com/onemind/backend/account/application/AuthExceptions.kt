package com.onemind.backend.account.application

import com.onemind.backend.common.error.RuleViolationException
import com.onemind.backend.common.error.TooManyRequestsException
import com.onemind.backend.common.error.UnauthorizedException
import java.time.Duration

class EmailCodeResendTooSoonException(
    retryAfter: Duration,
) : TooManyRequestsException(
        code = "EMAIL_CODE_RESEND_TOO_SOON",
        detail = "Wait a moment before asking for a new code.",
        retryAfter = retryAfter,
    )

class EmailCodeInvalidException(
    attemptsLeft: Int?,
) : RuleViolationException(
        code = "EMAIL_CODE_INVALID",
        detail = "That code isn't right. Check the email and try again.",
        params = attemptsLeft?.let { mapOf("attemptsLeft" to it) }.orEmpty(),
    )

class EmailCodeExpiredException :
    RuleViolationException(
        code = "EMAIL_CODE_EXPIRED",
        detail = "This code has expired. Ask for a new one.",
    )

class EmailCodeAttemptsExceededException :
    RuleViolationException(
        code = "EMAIL_CODE_ATTEMPTS_EXCEEDED",
        detail = "Too many wrong codes. Ask for a new one.",
    )

class RefreshTokenInvalidException :
    UnauthorizedException(
        code = "REFRESH_TOKEN_INVALID",
        detail = "Your session has ended. Sign in again.",
    )
