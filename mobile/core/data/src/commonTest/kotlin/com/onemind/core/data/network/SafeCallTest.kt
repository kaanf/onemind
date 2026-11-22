package com.onemind.core.data.network

import com.onemind.core.api.model.AuthTokens
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SafeCallTest {
    @Test
    fun successDecodesTheBody() =
        runTest {
            val api = TestApi(backgroundScope) { respondJson(tokensJson("a1")) }.start()

            val result = safeCall<AuthTokens> { api.client.get("x") }

            assertEquals("a1", assertIs<Result.Success<AuthTokens>>(result).data.accessToken)
        }

    @Test
    fun problemBecomesAnApiErrorWithStringParams() =
        runTest {
            val api =
                TestApi(backgroundScope) {
                    respondJson(problemJson(422, "EMAIL_CODE_INVALID", """{"attemptsLeft":3}"""), HttpStatusCode.UnprocessableEntity)
                }.start()

            val result = safeCall<Unit> { api.client.get("x") }

            assertEquals(
                DataError.Api(status = 422, code = "EMAIL_CODE_INVALID", detail = "English detail", params = mapOf("attemptsLeft" to "3")),
                assertIs<Result.Failure<DataError>>(result).error,
            )
        }

    @Test
    fun aBodyThatIsNotAProblemKeepsTheStatus() =
        runTest {
            val api = TestApi(backgroundScope) { respondError(HttpStatusCode.BadGateway, "<html>proxy</html>") }.start()

            val error = assertIs<Result.Failure<DataError>>(safeCall<Unit> { api.client.get("x") }).error

            assertEquals(DataError.Api(status = 502, code = UNKNOWN_CODE), error)
        }

    @Test
    fun unreadableSuccessBodyIsASerializationError() =
        runTest {
            val api = TestApi(backgroundScope) { respondJson("""{"unexpected":true}""") }.start()

            val error = assertIs<Result.Failure<DataError>>(safeCall<AuthTokens> { api.client.get("x") }).error

            assertEquals(DataError.Network.SERIALIZATION, error)
        }

    @Test
    fun ioFailureIsNoInternet() =
        runTest {
            val api = TestApi(backgroundScope) { throw IOException("offline") }.start()

            val error = assertIs<Result.Failure<DataError>>(safeCall<Unit> { api.client.get("x") }).error

            assertEquals(DataError.Network.NO_INTERNET, error)
        }

    @Test
    fun emptySuccessIsUnit() =
        runTest {
            val api = TestApi(backgroundScope) { respond("", HttpStatusCode.Accepted) }.start()

            assertIs<Result.Success<Unit>>(safeCall<Unit> { api.client.get("x") })
        }
}
