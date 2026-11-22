package com.onemind.backend.pos

import com.onemind.backend.TestcontainersConfiguration
import com.onemind.backend.common.security.AccessTokenIssuer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals

/** Without the demo POS (as in production) demo onboarding doesn't exist: `404 FEATURE_UNAVAILABLE`. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["db-scheduler.enabled=false"])
@AutoConfigureMockMvc
class DemoOnboardingOffTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var accessTokens: AccessTokenIssuer

    @Test
    fun `without a demo POS both endpoints answer 404 FEATURE_UNAVAILABLE and create nothing`() {
        val user =
            requireNotNull(
                jdbc.queryForObject<UUID>(
                    "insert into account.app_user (email) values (?) returning id",
                    "off-${UUID.randomUUID()}@example.com",
                ),
            )
        val token = "Bearer ${accessTokens.issue(user).value}"

        mockMvc.get("/api/v1/onboarding/demo-places") { header("Authorization", token) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FEATURE_UNAVAILABLE") }
        }
        mockMvc
            .post("/api/v1/onboarding/demo") {
                header("Authorization", token)
                header("Idempotency-Key", UUID.randomUUID().toString())
                contentType = MediaType.APPLICATION_JSON
                content = """{"placeIds": ["demo-letna"]}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("FEATURE_UNAVAILABLE") }
            }
        assertEquals(0, jdbc.queryForObject<Int>("select count(*) from account.membership where user_id = ?", user))
    }
}
