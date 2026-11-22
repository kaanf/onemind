package com.onemind.auth.domain

import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult

/** Passwordless email sign-in (A1, A2; ADR 0004). A successful [verifyCode] signs the device in. */
interface AuthRepository {
    /** `POST /auth/email/start`; the server answers `202` whether or not the account exists. */
    suspend fun sendCode(email: String): EmptyResult<DataError>

    /** `POST /auth/email/verify`; on success the tokens are stored and the session becomes signed in. */
    suspend fun verifyCode(
        email: String,
        code: String,
    ): EmptyResult<DataError>
}
