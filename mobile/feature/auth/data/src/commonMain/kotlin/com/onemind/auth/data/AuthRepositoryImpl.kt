package com.onemind.auth.data

import com.onemind.auth.domain.AuthRepository
import com.onemind.core.api.model.AuthTokens
import com.onemind.core.api.model.EmailCodeStartRequest
import com.onemind.core.api.model.EmailCodeVerifyRequest
import com.onemind.core.data.network.safeCall
import com.onemind.core.data.session.SessionManager
import com.onemind.core.data.session.StoredSession
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.EmptyResult
import com.onemind.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val sessions: SessionManager,
) : AuthRepository {
    override suspend fun sendCode(email: String): EmptyResult<DataError> =
        safeCall<Unit> { client.post("auth/email/start") { setBody(EmailCodeStartRequest(email = email.trim())) } }

    override suspend fun verifyCode(
        email: String,
        code: String,
    ): EmptyResult<DataError> {
        val result =
            safeCall<AuthTokens> {
                client.post("auth/email/verify") { setBody(EmailCodeVerifyRequest(email = email.trim(), code = code)) }
            }
        return when (result) {
            is Result.Failure -> {
                result
            }

            is Result.Success -> {
                try {
                    sessions.save(StoredSession.from(result.data))
                    Result.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Secure storage failed: the user can't stay signed in, so report it like any other failure.
                    Result.Failure(DataError.Network.UNKNOWN)
                }
            }
        }
    }
}
