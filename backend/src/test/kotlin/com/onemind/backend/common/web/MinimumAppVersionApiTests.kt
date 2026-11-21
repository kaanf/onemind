package com.onemind.backend.common.web

import com.onemind.backend.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** `426` below the platform minimum (ADR 0001), against real endpoints. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["onemind.app-version.minimum-ios=1.4.0", "onemind.app-version.minimum-android=2.0"])
@AutoConfigureMockMvc
class MinimumAppVersionApiTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `an app below its platform's minimum gets 426 with the minimum`() {
        refresh("IOS", "1.3.9").andExpectProblem(426, "APP_UPGRADE_REQUIRED").andExpect {
            jsonPath("$.params.platform") { value("IOS") }
            jsonPath("$.params.minimumVersion") { value("1.4.0") }
        }
        refresh("ANDROID", "1.99.0").andExpectProblem(426, "APP_UPGRADE_REQUIRED").andExpect {
            jsonPath("$.params.minimumVersion") { value("2.0") }
        }
    }

    @Test
    fun `the version is checked before authentication`() {
        mockMvc
            .delete("/api/v1/me") {
                header("X-Platform", "IOS")
                header("X-App-Version", "1.0")
            }.andExpectProblem(426, "APP_UPGRADE_REQUIRED")
    }

    @Test
    fun `the minimum and newer versions pass, compared by number`() {
        listOf("1.4.0", "1.4", "1.10.0", "2.0.0+31").forEach { refresh("IOS", it).andExpectProblem(401, "REFRESH_TOKEN_INVALID") }
        refresh("ANDROID", "2.0.0").andExpectProblem(401, "REFRESH_TOKEN_INVALID")
    }

    @Test
    fun `the platform header is case-insensitive`() {
        refresh("ios", "1.0.0").andExpectProblem(426, "APP_UPGRADE_REQUIRED")
    }

    @Test
    fun `requests without both headers or for another platform are not apps and pass`() {
        refresh(platform = null, version = "0.1").andExpectProblem(401, "REFRESH_TOKEN_INVALID")
        refresh(platform = "IOS", version = null).andExpectProblem(401, "REFRESH_TOKEN_INVALID")
        refresh(platform = "WEB", version = "0.1").andExpectProblem(401, "REFRESH_TOKEN_INVALID")
    }

    @Test
    fun `a version that is not a number is a 400`() {
        refresh("IOS", "latest").andExpectProblem(400, "APP_VERSION_INVALID")
    }

    @Test
    fun `health checks are never blocked`() {
        mockMvc
            .get("/actuator/health") {
                header("X-Platform", "IOS")
                header("X-App-Version", "0.1")
            }.andExpect { status { isOk() } }
    }

    private fun refresh(
        platform: String?,
        version: String?,
    ) = mockMvc.post("/api/v1/auth/refresh") {
        platform?.let { header("X-Platform", it) }
        version?.let { header("X-App-Version", it) }
        contentType = MediaType.APPLICATION_JSON
        content = """{"refreshToken": "unknown"}"""
    }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.code") { value(code) }
        jsonPath("$.traceId") { exists() }
    }
}
