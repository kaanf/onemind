package com.onemind.backend.common.idempotency

import com.onemind.backend.common.error.ConflictException
import com.onemind.backend.common.error.InvalidRequestException
import com.onemind.backend.common.error.RuleViolationException

class InvalidIdempotencyKeyException :
    InvalidRequestException(
        code = "INVALID_IDEMPOTENCY_KEY",
        detail = "The Idempotency-Key header must be a UUID.",
    )

class IdempotencyKeyReusedException :
    RuleViolationException(
        code = "IDEMPOTENCY_KEY_REUSED",
        detail = "This Idempotency-Key was already used for a different request.",
    )

class IdempotentRequestInProgressException :
    ConflictException(
        code = "IDEMPOTENT_REQUEST_IN_PROGRESS",
        detail = "A request with this Idempotency-Key is still being processed. Try again shortly.",
    )
