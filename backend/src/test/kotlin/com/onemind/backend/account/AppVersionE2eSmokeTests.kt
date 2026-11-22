package com.onemind.backend.account

import com.onemind.api.v1.client.model.EmailCodeStartRequest
import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.testing.ApiClient
import com.onemind.backend.testing.ApiClient.Companion.expectProblem
import com.onemind.backend.testing.RecordingMailSender
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals

/** The 426 flow of the auth smoke test, in its own context: only this class sets an iOS minimum. */
@Import(TestcontainersConfiguration::class, AppVersionE2eSmokeTests.Config::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["onemind.app-version.minimum-ios=2.0.0"],
)
class AppVersionE2eSmokeTests {
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean
        @Primary
        fun recordingMailSender() = RecordingMailSender()
    }

    @LocalServerPort
    var port = 0

    @Autowired
    lateinit var mapper: JsonMapper

    private val email = EmailCodeStartRequest("e2e-${UUID.randomUUID()}@example.com")

    @Test
    fun `an app below the minimum version gets 426 APP_UPGRADE_REQUIRED, even on sign-in`() {
        val old = ApiClient(port, mapper, platform = "ios", appVersion = "1.9")

        val problem = expectProblem(HttpStatus.UPGRADE_REQUIRED) { old.auth.startEmailSignIn(email) }

        assertEquals("APP_UPGRADE_REQUIRED", problem.code)
        assertEquals(mapOf("platform" to "IOS", "minimumVersion" to "2.0.0"), problem.params)
    }

    @Test
    fun `an app at the minimum version passes`() {
        val current = ApiClient(port, mapper, platform = "ios", appVersion = "2.0")

        assertEquals(HttpStatus.ACCEPTED, current.auth.startEmailSignIn(email).statusCode)
    }
}
