package com.onemind.backend.common.observability

import com.onemind.backend.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class, RequestLoggingTests.ProbeConfig::class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension::class)
class RequestLoggingTests {
    @RestController
    class ProbeController {
        private val log = LoggerFactory.getLogger(javaClass)

        @GetMapping("/test/probe")
        fun probe(
            @RequestParam marker: String,
        ) = log.info("probe {}", marker)
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
                .authorizeHttpRequests { it.anyRequest().permitAll() }
                .build()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    private val json = JsonMapper.builder().build()

    @Test
    fun `log lines are JSON with traceId, spanId and the client's request id`(output: CapturedOutput) {
        val requestId = UUID.randomUUID().toString()

        mockMvc.get("/test/probe?marker=valid") { header(RequestIdFilter.HEADER, requestId) }

        val line = probeLine(output, "valid")
        assertEquals(requestId, line["requestId"].asString())
        assertTrue(line["traceId"].asString().matches(Regex("[0-9a-f]{32}")))
        assertTrue(line["spanId"].asString().matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun `an incoming traceparent is continued`(output: CapturedOutput) {
        val traceId = "4bf92f3577b34da6a3ce929d0e0e4736"

        mockMvc.get("/test/probe?marker=traceparent") {
            header("traceparent", "00-$traceId-00f067aa0ba902b7-01")
        }

        assertEquals(traceId, probeLine(output, "traceparent")["traceId"].asString())
    }

    @Test
    fun `a request id that is not a UUID is not logged`(output: CapturedOutput) {
        mockMvc.get("/test/probe?marker=invalid") { header(RequestIdFilter.HEADER, "forged\" value") }

        assertFalse(probeLine(output, "invalid").has("requestId"))
    }

    private fun probeLine(
        output: CapturedOutput,
        marker: String,
    ) = output.out
        .lines()
        .filter { it.startsWith("{") }
        .map { json.readTree(it) }
        .single { it["message"]?.asString() == "probe $marker" }
}
