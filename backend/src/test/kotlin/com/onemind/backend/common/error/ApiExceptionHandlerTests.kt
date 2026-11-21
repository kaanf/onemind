package com.onemind.backend.common.error

import com.onemind.backend.TestcontainersConfiguration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Import(TestcontainersConfiguration::class, ApiExceptionHandlerTests.ProbeConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
class ApiExceptionHandlerTests {
    class ReceiptAlreadyApproved :
        ConflictException(
            code = "RECEIPT_ALREADY_APPROVED",
            detail = "This receipt was already approved.",
            params = mapOf("approvedAt" to "2026-11-17T14:05:00Z"),
        )

    class MissingThing : InvalidRequestException("MISSING_THING", "Missing.")

    class NoVenueAccess : ForbiddenException("NO_VENUE_ACCESS", "No access.")

    class ReceiptNotFound : NotFoundException("RECEIPT_NOT_FOUND", "Not found.")

    class NegativeStock : RuleViolationException("NEGATIVE_STOCK", "Stock can't go below zero.")

    data class Body(
        @field:NotBlank val name: String,
        @field:Positive val quantity: Int,
    )

    @RestController
    class ProbeController {
        @GetMapping("/test/errors/{kind}")
        fun fail(
            @PathVariable kind: String,
        ): Unit =
            throw when (kind) {
                "invalid" -> MissingThing()
                "forbidden" -> NoVenueAccess()
                "not-found" -> ReceiptNotFound()
                "conflict" -> ReceiptAlreadyApproved()
                "rule" -> NegativeStock()
                else -> IllegalStateException("secret internal state")
            }

        @PostMapping("/test/errors/body")
        fun body(
            @Valid @RequestBody body: Body,
        ) = Unit

        @GetMapping("/test/errors/param")
        fun param(
            @RequestParam @Positive limit: Int,
        ) = Unit
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ProbeConfig {
        @Bean
        fun probeController() = ProbeController()

        @Bean
        @Order(0)
        fun probeSecurity(http: HttpSecurity): SecurityFilterChain =
            http
                .securityMatcher("/test/**")
                .csrf { it.disable() }
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `a domain exception becomes a problem with code, params and traceId`() {
        mockMvc.get("/test/errors/conflict").andExpect {
            status { isConflict() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("https://api.onemind.app/problems/receipt-already-approved") }
            jsonPath("$.title") { value("Receipt already approved") }
            jsonPath("$.status") { value(409) }
            jsonPath("$.code") { value("RECEIPT_ALREADY_APPROVED") }
            jsonPath("$.detail") { value("This receipt was already approved.") }
            jsonPath("$.params.approvedAt") { value("2026-11-17T14:05:00Z") }
            jsonPath("$.traceId") { value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")) }
            jsonPath("$.errors") { doesNotExist() }
        }
    }

    @Test
    fun `each domain category maps to its status`() {
        mapOf("invalid" to 400, "forbidden" to 403, "not-found" to 404, "rule" to 422).forEach { (kind, status) ->
            mockMvc.get("/test/errors/$kind").andExpect { status { isEqualTo(status) } }
        }
    }

    @Test
    fun `invalid body fields are listed in errors`() {
        mockMvc
            .post("/test/errors/body") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": " ", "quantity": -1}"""
            }.andExpectProblem(400, "VALIDATION_FAILED")
            .andExpect {
                jsonPath("$.errors.length()") { value(2) }
                jsonPath("$.errors[?(@.field == 'name')].code") { value("NOT_BLANK") }
                jsonPath("$.errors[?(@.field == 'quantity')].code") { value("POSITIVE") }
            }
    }

    @Test
    fun `invalid parameters are listed in errors`() {
        mockMvc
            .get("/test/errors/param?limit=0")
            .andExpectProblem(400, "VALIDATION_FAILED")
            .andExpect {
                jsonPath("$.errors[0].field") { value("limit") }
                jsonPath("$.errors[0].code") { value("POSITIVE") }
            }
    }

    @Test
    fun `malformed JSON is a bad request`() {
        mockMvc
            .post("/test/errors/body") {
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpectProblem(400, "BAD_REQUEST")
    }

    @Test
    fun `an unknown path is not found`() {
        mockMvc.get("/test/nothing-here").andExpectProblem(404, "NOT_FOUND")
    }

    @Test
    fun `an unexpected exception is an internal error that reveals nothing`() {
        mockMvc
            .get("/test/errors/boom")
            .andExpectProblem(500, "INTERNAL_ERROR")
            .andExpect { content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))) } }
    }

    private fun ResultActionsDsl.andExpectProblem(
        status: Int,
        code: String,
    ) = andExpect {
        status { isEqualTo(status) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.code") { value(code) }
        jsonPath("$.type") { value("https://api.onemind.app/problems/" + code.lowercase().replace('_', '-')) }
        jsonPath("$.traceId") { exists() }
    }
}
