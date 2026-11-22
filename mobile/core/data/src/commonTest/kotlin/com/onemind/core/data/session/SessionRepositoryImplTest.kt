package com.onemind.core.data.session

import com.onemind.core.data.network.TestApi
import com.onemind.core.data.network.respondJson
import com.onemind.core.data.network.session
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryImplTest {
    @Test
    fun signOutRevokesOnTheServerAndForgetsTheTokens() =
        runTest {
            val api = TestApi(backgroundScope, initialSession = session("a1")) { respondJson("", HttpStatusCode.NoContent) }.start()

            SessionRepositoryImpl(api.sessions, api.client).signOut()

            val logout = api.engine.requestHistory.single()
            assertTrue(logout.url.encodedPath.endsWith("auth/logout"))
            assertEquals("""{"refreshToken":"refresh-a1"}""", logout.bodyText())
            assertNull(api.sessions.current())
        }

    @Test
    fun signOutOfflineStillForgetsTheTokens() =
        runTest {
            val api = TestApi(backgroundScope, initialSession = session("a1")) { throw IOException("offline") }.start()

            SessionRepositoryImpl(api.sessions, api.client).signOut()

            assertNull(api.sessions.current())
            assertNull(api.store.value)
        }

    private fun HttpRequestData.bodyText(): String = ((body as OutgoingContent.ByteArrayContent).bytes()).decodeToString()
}
