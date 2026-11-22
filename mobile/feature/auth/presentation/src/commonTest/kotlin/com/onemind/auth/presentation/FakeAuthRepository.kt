package com.onemind.auth.presentation

import com.onemind.auth.domain.AuthRepository
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result

class FakeAuthRepository : AuthRepository {
    var sendResult: EmptyResult<DataError> = Result.Success(Unit)
    var verifyResult: EmptyResult<DataError> = Result.Success(Unit)
    val sent = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String>>()

    override suspend fun sendCode(email: String): EmptyResult<DataError> {
        sent += email
        return sendResult
    }

    override suspend fun verifyCode(
        email: String,
        code: String,
    ): EmptyResult<DataError> {
        verified += email to code
        return verifyResult
    }
}
