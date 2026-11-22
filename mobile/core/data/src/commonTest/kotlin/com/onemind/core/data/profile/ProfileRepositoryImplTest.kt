package com.onemind.core.data.profile

import com.onemind.core.data.network.TestApi
import com.onemind.core.data.network.awaitReal
import com.onemind.core.data.network.respondJson
import com.onemind.core.data.network.session
import com.onemind.core.domain.profile.Profile
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileRepositoryImplTest {
    private val me =
        """{"id":"u1","email":"owner@yourbar.cz","displayName":"Kaan Fırat",
            "memberships":[{"organizationId":"o1","organizationName":"Demo","role":"OWNER"}]}"""

    @Test
    fun keepsTheDisplayName() =
        runTest {
            val api = TestApi(backgroundScope, session("a1")) { respondJson(me) }.start()
            val repository = ProfileRepositoryImpl(api.client)

            awaitReal { repository.refresh() }

            assertEquals(Profile("Kaan Fırat"), repository.profile.value)
            assertEquals(
                "/api/v1/me",
                api.engine.requestHistory
                    .single()
                    .url.encodedPath,
            )
        }

    @Test
    fun aFailureKeepsTheLastProfileAndClearForgetsIt() =
        runTest {
            var fail = false
            val api =
                TestApi(
                    backgroundScope,
                    session("a1"),
                ) { if (fail) respondError(HttpStatusCode.ServiceUnavailable) else respondJson(me) }.start()
            val repository = ProfileRepositoryImpl(api.client)
            awaitReal { repository.refresh() }

            fail = true
            awaitReal { repository.refresh() }
            assertEquals(Profile("Kaan Fırat"), repository.profile.value)

            repository.clear()
            assertNull(repository.profile.value)
        }
}
